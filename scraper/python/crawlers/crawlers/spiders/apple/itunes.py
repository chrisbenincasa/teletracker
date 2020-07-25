import scrapy

from datetime import datetime

from scrapy.spiders import Rule
from scrapy.linkextractors import LinkExtractor

from crawlers.base_spider import BaseCrawlSpider
import dateutil.parser
import json
import isodate
import re

from crawlers.items import AppleTvCastMember
from crawlers.items import AppleTvCrewMember
from crawlers.items import AppleTvItem
from crawlers.items import AppleTvItemOffer

PRICE_RE = r'\$(\d+\.\d+)'


class ItunesSitemapSpider(BaseCrawlSpider):
    name = 'itunes'
    allowed_domains = ['apple.com']

    start_urls = [
        'https://itunes.apple.com/us/genre/movies/id33',
    ]

    rules = (
        Rule(LinkExtractor(allow=(r'(https?://itunes.apple.com)?/us/movie',)),
             callback='parse_movie', follow=True),
        Rule(LinkExtractor(
            allow=r'(https?://itunes.apple.com)?/us/genre/movies.+'), follow=True)
    )

    sitemap_rules = [
        ('/us/movie/', 'parse_movie')
    ]

    # sitemap_urls = [
    #     'http://sitemaps.itunes.apple.com/sitemap_index.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_1.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_2.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_3.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_4.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_5.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_6.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_7.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_8.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_9.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_10.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_31.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_41.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_51.xml',
    #     'http://sitemaps.itunes.apple.com/sitemap_index_61.xml',
    # ]
    #
    # def sitemap_filter(self, entries):
    #     self.log('Handling sitemap')
    #     for entry in entries:
    #         date_time = datetime.strptime(entry['lastmod'], '%Y-%m-%dT%H:%M:%S.%f%z')
    #         if date_time.year >= 2020 and 'audiobooks' not in entry['loc'] and 'itunes_artist' not in entry['loc']:
    #             yield entry

    # def parse(self, response):
    #     pass
    #

    def parse_movie(self, response):
        schema = response.xpath('//script[@name="schema:movie"]/text()').get()
        if schema:
            external_id = '/'.join(response.url.split('/')[-2:])
            loaded_schema = json.loads(schema)
            desc = response.css('.product-hero-desc p::text').get()

            offers = []
            prices = set([l.strip() for l in response.css('.movie-header__list--price span::text').getall()])
            for price in prices:
                matched_price = re.search(PRICE_RE, price)
                if matched_price:
                    actual_price = float(matched_price.group(1))
                    if 'Rent' in price:
                        offers.append(
                            AppleTvItemOffer(offerType='rent', price=actual_price, currency='USD', quality='HD'))
                    elif 'Buy' in price:
                        offers.append(
                            AppleTvItemOffer(offerType='buy', price=actual_price, currency='USD', quality='HD'))

            release_date_sel = response.css('.movie-header__list__item--release-date time')
            release_date = None
            release_year = None
            if len(release_date_sel) > 0:
                release_year = int(release_date_sel[0].xpath('.//text()').get())
                release_date = dateutil.parser.isoparse(release_date_sel[0].attrib['datetime'])

            cast = []
            crew = []
            if 'actor' in loaded_schema:
                for (idx, actor) in enumerate(loaded_schema['actor']):
                    cast.append(AppleTvCastMember(name=actor['name'], order=idx, role='actor'))

            if 'director' in loaded_schema or 'producer' in loaded_schema:
                directors = loaded_schema['director'] if 'director' in loaded_schema else []
                producers = loaded_schema['producer'] if 'producer' in loaded_schema else []
                order = 0
                for director in directors:
                    crew.append(AppleTvCrewMember(name=director['name'], order=order, role='director'))
                    order += 1

                for producer in producers:
                    crew.append(AppleTvCrewMember(name=producer['name'], order=order, role='producer'))
                    order += 1

            runtime = None
            if 'duration' in loaded_schema:
                runtime = isodate.parse_duration(loaded_schema['duration']).seconds

            yield AppleTvItem(
                id=external_id,
                title=loaded_schema['name'],
                externalId=external_id,
                description=desc,
                itemType='movie',
                network='apple-tv',
                url=response.url,
                releaseDate=release_date,
                releaseYear=release_year,
                cast=cast,
                crew=crew,
                runtime=runtime,
                offers=offers,
            )
