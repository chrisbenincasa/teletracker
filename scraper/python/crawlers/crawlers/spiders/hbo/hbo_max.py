import json

import scrapy

from crawlers.base_spider import BaseSitemapSpider
from urllib.parse import unquote

_client_id = '585b02c8-dbe1-432f-b1bb-11cf670fbeb0'
_client_secret = '585b02c8-dbe1-432f-b1bb-11cf670fbeb0'
_scope = 'browse video_playback_free'
_grant_type = 'client_credentials'
_device_serial_number = '7de92ed7-d64c-4534-9dd8-fca4cd105262'

CONTENT_API_URL = 'https://comet.api.hbo.com/content'

def _load_script_data(response):
    data = response.xpath('//script[@id="__NEXT_DATA__"]/text()').get()
    if data:
        return json.loads(data)


def _extract_metadata_object(loaded_data):
    mapped_data = loaded_data['props']['pageProps']['mappedData']
    for key in mapped_data:
        if type(mapped_data[key]) == dict and 'category' in mapped_data[key]:
            print('Found metadata: {}'.format(key))
            return mapped_data[key]


def _extract_seasons_object(loaded_data):
    mapped_data = loaded_data['props']['pageProps']['mappedData']
    for key in mapped_data:
        if type(mapped_data[key]) == list:
            for obj in mapped_data[key]:
                if 'seasonId' in obj:
                    print('Found seasons: {}'.format(key))
                    return mapped_data[key]


class HboNowSpider(BaseSitemapSpider):
    name = 'hbomax'
    allowed_domains = ['hbomax.com', 'comet.api.hbo.com']

    sitemap_urls = [
        "https://www.hbomax.com/static/sitemap.xml"
    ]

    sitemap_rules = [
        (r'https://www.hbomax.com/series/urn.*', 'parse_series'),
        (r'https://www.hbomax.com/feature/urn.*', 'parse_movie')
    ]

    custom_settings = {
        'DOWNLOAD_DELAY': 0.5
    }

    def parse_series(self, response):
        loaded_data = _load_script_data(response)
        if loaded_data:
            metadata = _extract_metadata_object(loaded_data)
            seasons = list(self._extract_seasons(metadata))

            externalId = loaded_data['query']['id']
            yield HboMaxItem(
                id=externalId,
                externalId=externalId,
                description=self._get_description_from_metadata(metadata),
                itemType='show',
                title=self._get_title_from_metadata(metadata),
                network='hbo-max',
                seasons=seasons,
                url=response.url
            )

    def parse_movie(self, response):
        loaded_data = _load_script_data(response)
        if loaded_data:
            metadata = _extract_metadata_object(loaded_data)

            externalId = loaded_data['query']['id']
            yield HboMaxItem(
                id=externalId,
                externalId=externalId,
                itemType='movie',
                description=self._get_description_from_metadata(metadata),
                title=self._get_title_from_metadata(metadata),
                network='hbo-max',
                url=response.url,
                couldBeOnHboGo=self._could_be_on_hbo(metadata)
            )

    def _could_be_on_hbo(self, metadata):
        if metadata and 'title' in metadata and 'en_US' in metadata['title']:
            return metadata['title']['en_US']['full'].endswith('(HBO)')

    def _get_title_from_metadata(self, metadata):
        if metadata and 'title' in metadata and 'en_US' in metadata['title']:
            return metadata['title']['en_US']['full'].rstrip('(HBO)').strip()

    def _get_description_from_metadata(self, metadata):
        if metadata and 'summary' in metadata and 'en_US' in metadata['summary']:
            return metadata['summary']['en_US']['full'].strip()

    def _extract_seasons(self, metadata):
        if metadata and 'seasons' in metadata:
            for season in metadata['seasons']:
                yield HboMaxSeasonItem(
                    id=season['seasonId'],
                    title=season['orgtitle'],
                    seasonNumber=season['seasonNumber'],
                    episodeCount=season['numberOfEpisodes'],
                    description=season['summary']['en_US']['full'],
                    episodes=list(self._extract_episodes(season))
                )

    def _extract_episodes(self, season):
        for episode in season['episodes']:
            yield HboMaxEpisodeItem(
                episodeNumber=episode['episodeNumber'],
                quality=episode['quality'],
                title=episode['title']['en_US']['full_original'],
                description=season['summary']['en_US']['full'],
            )


class HboMaxItem(scrapy.Item):
    type = 'HboMaxItem'
    id = scrapy.Field()
    externalId = scrapy.Field()
    itemType = scrapy.Field()
    description = scrapy.Field()
    network = scrapy.Field()
    title = scrapy.Field()
    seasons = scrapy.Field()
    url = scrapy.Field()
    couldBeOnHboGo = scrapy.Field()
    releaseYear = scrapy.Field()
    cast = scrapy.Field()
    crew = scrapy.Field()
    runtime = scrapy.Field()


class HboMaxSeasonItem(scrapy.Item):
    id = scrapy.Field()
    title = scrapy.Field()
    seasonNumber = scrapy.Field()
    description = scrapy.Field()
    episodeCount = scrapy.Field()
    episodes = scrapy.Field()


class HboMaxEpisodeItem(scrapy.Item):
    episodeNumber = scrapy.Field()
    quality = scrapy.Field()
    title = scrapy.Field()
    description = scrapy.Field()
