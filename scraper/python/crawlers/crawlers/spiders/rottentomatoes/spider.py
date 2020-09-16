import json

import dateutil.parser
import scrapy

from crawlers.base_spider import BaseSitemapSpider
from crawlers.items import BaseItem
from urllib.parse import urlparse


class RottenTomatoesSpider(BaseSitemapSpider):
    name = 'rottentomatoes'
    store_name = name

    allowed_domains = ['rottentomatoes.com']

    sitemap_urls = ['https://www.rottentomatoes.com/sitemap.xml']

    sitemap_rules = [
        ('/m/[A-z0-9_-]+$', 'parse_movie'),
        ('/tv/[A-z0-9_-]+$', 'parse_show')
    ]

    def parse_movie(self, response):
        audience_rating_pct = response.xpath('//a[@href="#audience_reviews"]').css(
            '.mop-ratings-wrap__percentage::text').get().strip()
        total_audience_rating_count = response.css(
            '.audience-score .mop-ratings-wrap__text--small::text').get().lower().lstrip('user ratings: ')

        script_contents = response.xpath(
            '//script[@type="application/ld+json"]/text()').get()

        if script_contents:
            loaded = json.loads(script_contents)
            external_id = urlparse(response.url).path

            description = response.css('#movieSynopsis::text').get()

            release_date = response.xpath(
                '//div'
                '[contains(concat(\' \',normalize-space(@class),\' \'),\' meta-label \')]'
                '[contains(text(), "In Theaters")]/following-sibling::*/time/@datetime').get()

            parsed_date = None
            if release_date:
                parsed_date = dateutil.parser.isoparse(release_date)

            critic_score = None
            num_critic_reviews = None
            if 'aggregateRating' in loaded:
                critic_score = loaded['aggregateRating']['ratingValue'] if 'ratingValue' in loaded[
                    'aggregateRating'] else None
                num_critic_reviews = loaded['aggregateRating']['reviewCount'] if 'reviewCount' in loaded[
                    'aggregateRating'] else None

            cast = []
            crew = []
            if loaded and 'actors' in loaded:
                for (idx, actor) in enumerate(loaded['actors']):
                    cast.append(RottenTomatoesCastMember(name=actor['name'], order=idx))

            if loaded:
                if 'creator' in loaded:
                    for (idx, creator) in enumerate(loaded['creator']):
                        crew.append(RottenTomatoesCrewMember(
                            name=creator['name'], order=idx, role='Creator'))
                if 'director' in loaded:
                    for (idx, director) in enumerate(loaded['director']):
                        crew.append(RottenTomatoesCrewMember(
                            name=director['name'], order=idx, role='Director'))

            yield RottenTomatoesItem(
                id=external_id,
                title=loaded['name'] if 'name' in loaded else None,
                description=description.strip() if description else None,
                releaseYear=parsed_date.year if parsed_date else None,
                externalId=external_id,
                itemType='movie',
                network='rottentomatoes',
                audienceScore=int(audience_rating_pct.replace('%', '')) if audience_rating_pct else None,
                audienceCount=int(
                    total_audience_rating_count.replace(',', '')) if total_audience_rating_count else None,
                criticScore=critic_score,
                criticCount=num_critic_reviews,
                cast=cast,
                crew=crew
            )

        self.log('got thingy {}'.format(response.url))

    def parse_show(self, response):
        self.log('got tv {}'.format(response.url))


class RottenTomatoesItem(BaseItem):
    type = 'rottentomatoes'
    id = scrapy.Field()
    title = scrapy.Field()
    releaseYear = scrapy.Field()
    description = scrapy.Field()
    externalId = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    audienceScore = scrapy.Field()
    audienceCount = scrapy.Field()
    criticScore = scrapy.Field()
    criticCount = scrapy.Field()
    cast = scrapy.Field()
    crew = scrapy.Field()


class RottenTomatoesCastMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class RottenTomatoesCrewMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()
