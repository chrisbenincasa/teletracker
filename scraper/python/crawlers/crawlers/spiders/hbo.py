import json
from os import path
from urllib import parse

import scrapy

from crawlers.base_spider import BaseSitemapSpider
from crawlers.items import HboItem
from crawlers.util import strip_tags


def _get_band_of_type(react_data, typ):
    try:
        if 'bands' in react_data:
            for band in react_data['bands']:
                if band['band'] == typ:
                    return band
    except KeyError:
        return None


def _is_series_data(react_data):
    try:
        content_overview = _get_band_of_type(react_data, 'ContentOverview')
        if content_overview:
            return content_overview['data']['infoSlice']['streamingId']['type'] == 'series'
    except KeyError:
        return False


def _get_synopsis(react_data):
    try:
        synopsis_band = _get_band_of_type(react_data, 'Synopsis')
        if synopsis_band:
            return strip_tags(synopsis_band['data']['summary'])
    except KeyError:
        return None


def _get_movie_description(react_data):
    try:
        synopsis_band = _get_band_of_type(react_data, 'Text')
        if synopsis_band:
            return strip_tags(synopsis_band['data']['content'])
    except KeyError:
        return None


def _get_streaming_id(react_data):
    try:
        content_overview = _get_band_of_type(react_data, 'ContentOverview')
        if content_overview:
            return content_overview['data']['infoSlice']['streamingId']['id']
    except KeyError:
        return None


def _get_page_schema_with_type(react_data, typ):
    if 'pageSchemaList' not in react_data:
        return None

    for schema in react_data['pageSchemaList']:
        try:
            if schema['@type'] == typ:
                return schema
        except KeyError:
            continue

def _maybe_get_movie_id_from_image(react_data):
    try:
        content_overview = _get_band_of_type(react_data, 'ContentOverview')
        if content_overview:
            image_src = parse.urlparse(content_overview['data']['image']['images'][0]['src'])
            parts = image_src.path.split('/')
            if 'images' in parts and 'tilezoom' in parts:
                return 'urn:hbo:feature:{}'.format(parts[2])
    except (KeyError, IndexError):
        return None


def _parse_series_programs_json(loaded_json, partial_item):
    try:
        eps = loaded_json['programs']
        if eps and len(eps) > 0:
            first_ep = eps[0]
            partial_item['id'] = path.split(first_ep['series']['goUrl'])[-1]
            partial_item['goUrl'] = first_ep['series']['goUrl']
            partial_item['nowUrl'] = first_ep['series']['nowUrl']
            partial_item['releaseDate'] = first_ep['publishDate']

            return partial_item
    except KeyError:
        return partial_item


def _parse_movie_programs_json(loaded_json, partial_item):
    try:
        movies = loaded_json['programs']
        if movies and len(movies) > 0:
            movie = movies[0]
            if not partial_item['id']:
                external_id = path.split(movie['availability']['go'][0]['url'])[-1]
                partial_item['id'] = external_id
                partial_item['externalId'] = external_id

            partial_item['goUrl'] = movie['availability']['go'][0]['url']
            partial_item['nowUrl'] = movie['availability']['now'][0]['url']
            partial_item['releaseDate'] = movie['publishDate']
            partial_item['highDef'] = movie['hd']

            return partial_item
    except KeyError:
        return partial_item


class HboSpider(BaseSitemapSpider):
    name = 'hbo'
    allowed_domains = ['hbo.com']

    sitemap_urls = [
        "https://www.hbo.com/sitemap.xml"
    ]

    sitemap_rules = [
        (r'https:\/\/www.hbo.com\/[A-z\-]+$', 'parse_series'),
        (r'/movies/[A-z-]+/?$', 'parse_movie')
    ]

    custom_settings = {
        'DOWNLOAD_DELAY': 0.5
    }

    def parse_series(self, response):
        loaded = json.loads(response.xpath('//noscript[@id="react-data"]/@data-state').get())

        if loaded and _is_series_data(loaded):
            series_data = _get_page_schema_with_type(react_data=loaded, typ='TVSeries')
            streaming_id = _get_streaming_id(react_data=loaded)
            if series_data and streaming_id:
                synopsis = _get_synopsis(loaded)
                item = HboItem(
                    title=series_data['name'],
                    description=synopsis,
                    itemType='show',
                    network='hbo'
                )

                yield scrapy.Request(
                    'https://proxy-v4.cms.hbo.com/v1/schedule/programs?seriesIds={}'.format(streaming_id),
                    callback=self.finish_parse_series,
                    meta={'item': item})

    def finish_parse_series(self, response):
        item = response.meta['item']
        loaded = json.loads(response.body_as_unicode())
        yield _parse_series_programs_json(loaded, item)

    def parse_movie(self, response):
        loaded = json.loads(response.xpath('//noscript[@id="react-data"]/@data-state').get())
        if loaded:
            movie_data = _get_page_schema_with_type(react_data=loaded, typ='movie')
            streaming_id = _get_streaming_id(react_data=loaded)
            if movie_data and streaming_id:
                synopsis = _get_movie_description(react_data=loaded)
                external_id = _maybe_get_movie_id_from_image(loaded)

                go_url, now_url = None, None
                if external_id:
                    go_url = 'https://play.hbogo.com/feature/urn:hbo:feature:{}'.format(external_id)
                    now_url = 'https://play.hbonow.com/feature/urn:hbo:feature:{}'.format(external_id)

                item = HboItem(
                    id=external_id,
                    externalId=external_id,
                    title=movie_data['name'],
                    description=synopsis,
                    itemType='movie',
                    network='hbo',
                    goUrl=go_url,
                    nowUrl=now_url
                )

                yield scrapy.Request(
                    'https://proxy-v4.cms.hbo.com/v1/schedule/programs?productIds={}'.format(streaming_id),
                    callback=self.finish_parse_movie,
                    meta={'item': item})

    def finish_parse_movie(self, response):
        item = response.meta['item']
        loaded = json.loads(response.body_as_unicode())
        yield _parse_movie_programs_json(loaded, item)
