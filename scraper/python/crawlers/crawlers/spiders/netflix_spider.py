import scrapy
import json
import logging
import re

from scrapy.spiders import Rule
from scrapy.linkextractors import LinkExtractor

from crawlers.base_spider import BaseCrawlSpider
from crawlers.items import NetflixItem, NetflixItemSeason, NetflixItemEpisode


def _safe_to_int(value):
    try:
        return int(value.strip())
    except ValueError:
        return None


season_number_re = re.compile(r'.*\s+(\d+)')
limited_series_re = re.compile('Limited Series')


class NetflixSpider(BaseCrawlSpider):
    name = "netflix"

    allowed_domains = ['netflix.com']

    start_urls = [
        'https://www.netflix.com/browse/genre/839338',
        'https://www.netflix.com/browse/genre/34399',
        'https://www.netflix.com/browse/genre/83'
    ]

    custom_settings = {
        'DOWNLOAD_DELAY': 1,
    }

    rules = (
        Rule(LinkExtractor(allow=(r'(https://www.netflix.com)?/title/\d+',)),
             callback='parse_item', follow=True),
        Rule(LinkExtractor(
            allow=r'https://www.netflix.com/browse/genre/\d+'), follow=True)
    )

    def parse_item(self, response):
        id = response.url.split('/')[-1]
        schema_org_json = self._attempt_to_load_schema_json(id, response)

        title = schema_org_json['name'] if schema_org_json and schema_org_json['name'] else self._extract_title(
            response)

        description = schema_org_json['description'] if schema_org_json and schema_org_json[
            'description'] else self._extract_description(
            response)

        content_rating = schema_org_json['contentRating'] if schema_org_json and schema_org_json[
            'contentRating'] else self._extract_content_rating(
            response
        )

        if schema_org_json and schema_org_json['actors']:
            actors = [actor['name'] for actor in schema_org_json['actors']]
        else:
            actors = self._extract_actors(response)

        director = None
        if schema_org_json and schema_org_json['director']:
            director = schema_org_json['director'][0]['name']

        creator = None
        if schema_org_json and schema_org_json['creator']:
            creator = schema_org_json['creator'][0]['name']

        item_type = self._extract_type(schema_org_json)

        seasons = None
        if item_type is 'show':
            seasons = self._extract_seasons(response)

        yield NetflixItem(
            id=id,
            title=title,
            releaseYear=self._extract_release_year(response),
            network='Netflix',
            itemType=self._extract_type(schema_org_json),
            externalId=id,
            description=description,
            genres=item_type,
            contentRating=content_rating,
            actors=actors,
            director=director,
            creator=creator,
            seasons=seasons)

    def _attempt_to_load_schema_json(self, item_id, response):
        script_contents = response.xpath(
            '//script[@type="application/ld+json"]/text()').get()

        if script_contents:
            try:
                return json.loads(script_contents)
            except json.JSONDecodeError:
                self.log('Could not load schema.org JSON for item {}'.format(
                    item_id), level=logging.WARN)
                return None

    def _extract_title(self, response):
        return response.css('.title-title::text').get()

    def _extract_release_year(self, response):
        release_year = response.xpath(
            '//*[@data-uia="item-year"]/text()').get()
        try:
            return int(release_year.strip())
        except ValueError:
            return None

    def _extract_type(self, schema_org_json):
        if schema_org_json and schema_org_json['@type']:
            if schema_org_json['@type'] == 'Movie':
                return 'movie'
            elif schema_org_json['@type'] == 'TVSeries':
                return 'show'

    def _extract_description(self, response):
        return response.xpath(
            '//div[@data-uia="title-info-synopsis"]/text()').get()

    def _extract_all_genres(self, response):
        return [x.strip() for x in response.xpath('//*[@data-uia="more-details-item-genres"]//text()').getall() if
                x.strip() is not ',']

    def _extract_content_rating(self, response):
        rating = response.css('.maturity-rating').xpath('.//text()').get()
        if rating:
            return rating.strip()

    def _extract_actors(self, response):
        return [actor.strip() for actor in response.xpath(
            '//*[@data-uia="more-details-item-cast"]/text()')]

    def _extract_seasons(self, response):
        seasons = []
        season_names = self._extract_season_numbers(response)
        for (idx, season) in enumerate(response.css('.season')):
            season_number = season_names[idx]["num"]
            release_year = season.xpath(
                './/*[@data-uia="season-release-year"]/text()').get()
            if release_year:
                release_year = _safe_to_int(
                    release_year.lstrip('Release year:').strip(' '))

            description = season.xpath(
                './/*[@data-uia="season-synopsis"]/text()').get()
            episodes = []

            for (episode_idx, episode) in enumerate(season.css('.episode')):
                episode_number = episode_idx + 1
                runtime = episode.xpath(
                    './/*[@data-uia="episode-runtime"]/text()').get()
                description = episode.xpath(
                    './/*[@data-uia="episode-synopsis"]/text()').get()
                name = episode.xpath(
                    './/*[@data-uia="episode-title"]/text()').get()
                if name:
                    name = name.lstrip('{}.'.format(episode_number)).strip()
                episodes.append(
                    NetflixItemEpisode(
                        seasonNumber=season_number,
                        episodeNumber=episode_number,
                        name=name,
                        runtime=runtime,
                        description=description
                    )
                )

            seasons.append(
                NetflixItemSeason(
                    seasonNameRaw=season_names[idx]["name"],
                    seasonNumber=season_number,
                    releaseYear=release_year,
                    description=description,
                    episodes=episodes
                )
            )

        return seasons

    def _extract_season_numbers(self, response):
        multiple_seasons = response.xpath(
            '//select[@data-uia="season-selector"]/option')
        seasons = []
        if multiple_seasons and len(multiple_seasons) > 0:
            for (idx, season) in enumerate(multiple_seasons):
                name = season.xpath('./text()').get()
                seasons.append({"name": name, "num": idx + 1})
        else:
            single_season = response.xpath(
                '//div[@data-uia="season-static-label"]/text()').get()
            if single_season:
                match = season_number_re.search(single_season)
                if match:
                    seasons.append({"name": single_season,
                                    "num": int(match.group(1)) if match else None})
                else:
                    match = limited_series_re.search(single_season)
                    if match:
                        seasons.append({"name": single_season,
                                        "num": 1})

        return seasons
