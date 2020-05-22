import json

import scrapy

from crawlers.base_spider import BaseSitemapSpider
from urllib.parse import urlparse, urljoin
from base64 import b64encode

from crawlers.items import ShowtimeItem, ShowtimeItemSeason, ShowtimeItemEpisode


def try_decode_json(s):
    try:
        return json.loads(s)
    except json.JSONDecodeError:
        return

def _schema_org_details(response, type):
    scripts = response.xpath('//script[@type="application/ld+json"]/text()').getall()
    for script in scripts:
        parsed = try_decode_json(script)
        if parsed and parsed['@context'] == 'https://schema.org' and parsed['@type'] == type:
            return parsed


def _movie_page_details(response):
    return _schema_org_details(response, 'TVSeries')


def _show_page_details(response):
    return _schema_org_details(response, 'TVSeries')


def _season_page_details(response):
    return _schema_org_details(response, 'TVSeason')


def _episode_page_details(response):
    scripts = response.xpath('//script[@type="application/ld+json"]/text()').getall()
    for script in scripts:
        parsed = try_decode_json(script)
        if not parsed:
            # Showtime episode pages have bad JSON
            parsed = try_decode_json(script.strip().rstrip('}'))

        if parsed and parsed['@context'] == 'https://schema.org' and parsed['@type'] == 'TVEpisode':
            return parsed


def _publication_date(details):
    if details is None:
        return

    try:
        return details['releasedEvent']['startDate']
    except KeyError:
        return None


class ShowtimeSpider(BaseSitemapSpider):
    name = 'showtime'
    allowed_domains = ['sho.com']

    sitemap_urls = [
        "http://www.sho.com/sitemap.xml"
    ]

    sitemap_rules = [
        ('/titles/', 'parse_title'),
        (r'https://www.sho.com/[A-z0-0\-]+$', 'parse_show')
    ]

    def parse_title(self, response):
        page_details = _movie_page_details(response)
        if page_details:
            external_id = b64encode(response.url.encode('utf-8')).decode('utf-8')
            description = page_details['description']
            if not description:
                description = response.css('.about-the-series-section p.block-container__copy::text').get()

            yield ShowtimeItem(
                id=external_id,
                externalId=external_id,
                title=page_details['name'],
                description=description,
                network='showtime',
                itemType='movie',
                url=response.url,
            )

    def parse_show(self, response):
        show_page_details = _show_page_details(response)
        if show_page_details:
            page_path = urlparse(response.url)
            description = response.css('.about-the-series-section p.block-container__copy::text').get()
            season_links = [link.attrib['href'] for link in
                            response.xpath('//a[contains(@href, "{}/season")]'.format(page_path.path))]
            season_links.sort()
            external_id = b64encode(response.url.encode('utf-8')).decode('utf-8')
            item = ShowtimeItem(
                id=external_id,
                externalId=external_id,
                title=show_page_details['name'],
                description=description,
                network='showtime',
                itemType='show',
                url=response.url,
                seasons=list()
            )

            yield self._next_season_request(item=item, base_url=response.url, seasons_left=season_links)

    def parse_season(self, response):
        item = response.meta['item']

        page_details = _season_page_details(response)
        if page_details:
            season_number = None
            for el in response.xpath('//meta[@name="page-tracking"]'):
                try:
                    season_number = int(el.attrib['content'].split(':')[-1])
                except ValueError:
                    pass

            description = response.css('p.hero__description::text').get()

            episode_links = [x.attrib['href'] for x in response.css('div.promo-season-group a.promo__link')]

            season = ShowtimeItemSeason(
                seasonNumber=season_number,
                description=description,
                releaseDate=_publication_date(page_details),
                episodes=list()
            )

            item['seasons'].append(season)

            self.log('{}'.format(episode_links))

            yield self._next_episode_request(item=item, base_url=response.meta['base_url'],
                                             seasons_left=response.meta['seasons_left'], episodes_left=episode_links)

        else:
            print("no page details")
            yield self._next_season_request(item=item, base_url=response.meta['base_url'],
                                            seasons_left=response.meta['seasons_left'])

    def _next_season_request(self, item, base_url, seasons_left):
        if len(seasons_left) > 0:
            next_season, *rest = seasons_left
            return scrapy.Request(urljoin(base_url, next_season), callback=self.parse_season,
                                  meta={'item': item, 'seasons_left': rest, 'base_url': base_url})
        else:
            return item

    def _next_episode_request(self, item, base_url, seasons_left, episodes_left):
        if len(episodes_left) > 0:
            next_episode, *rest = episodes_left
            return scrapy.Request(urljoin(base_url, next_episode), callback=self.parse_episode,
                                  meta={'item': item, 'episodes_left': rest, 'base_url': base_url,
                                        'seasons_left': seasons_left})
        else:
            return self._next_season_request(item=item, base_url=base_url, seasons_left=seasons_left)

    def parse_episode(self, response):
        item = response.meta['item']
        page_details = _episode_page_details(response)
        episode_number = None
        for el in response.xpath('//meta[@name="page-tracking"]'):
            try:
                episode_number = int(el.attrib['content'].split(':')[-1])
            except ValueError:
                pass

        description = response.css('p.hero__description::text').get()

        episode = ShowtimeItemEpisode(
            episodeNumber=episode_number,
            description=description,
            releaseDate=_publication_date(page_details)
        )

        item['seasons'][-1]['episodes'].append(episode)

        yield self._next_episode_request(item=item, base_url=response.meta['base_url'],
                                         seasons_left=response.meta['seasons_left'],
                                         episodes_left=response.meta['episodes_left'])
