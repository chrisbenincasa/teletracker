import scrapy
import json

from crawlers.base_spider import BaseSpider
from crawlers.items import HboItem
from crawlers.spiders.hbo.hbo import _get_page_schema_with_type, _parse_movie_programs_json, \
    _maybe_get_movie_id_from_image, _get_movie_description, _get_streaming_id, _is_series_data, _get_synopsis, \
    _parse_series_programs_json


class HboCrawlSpider(BaseSpider):
    name = 'hbo_catalog'
    allowed_domains = ['hbo.com']

    start_urls = [
        'https://www.hbo.com/movies/catalog'
    ]

    def start_requests(self):
        yield scrapy.Request(url='https://www.hbo.com/movies/catalog', callback=self.parse_movie_catalog_page,
                             cb_kwargs={'type': 'movie'})
        yield scrapy.Request(url='https://www.hbo.com/series/all-series', callback=self.parse_series_page)
        yield scrapy.Request(url='https://www.hbo.com/special/all-specials', callback=self.parse_specials_page)
        yield scrapy.Request(url='https://www.hbo.com/documentaries/catalog', callback=self.parse_movie_catalog_page,
                             cb_kwargs={'type': 'documentary'})
        # TODO: Add sports docs section

    def parse(self, response):
        pass

    def parse_series_page(self, response):
        data_state = response.xpath('//noscript[@id="react-data"]/@data-state').get()
        loaded = json.loads(data_state)

        for band in loaded['bands']:
            data = band['data']
            if data and 'type' in data and data['type'] == 'series':
                for series in data['entries']:
                    if 'cta' not in series or 'href' not in series['cta']:
                        for req in self.parse_series_from_catalog(series):
                            yield req
                    else:
                        yield scrapy.Request('https://www.hbo.com{}'.format(series['cta']['href']),
                                             callback=self.parse_series)

    def parse_specials_page(self, response):
        data_state = response.xpath('//noscript[@id="react-data"]/@data-state').get()
        loaded = json.loads(data_state)

        for band in loaded['bands']:
            data = band['data']
            if data and 'type' in data and data['type'] == 'series':
                for series in data['entries']:
                    if 'cta' not in series or 'href' not in series['cta']:
                        for req in self.parse_movie_from_catalog(series):
                            yield req
                    else:
                        yield scrapy.Request('https://www.hbo.com{}'.format(series['cta']['href']),
                                             callback=self.parse_series)

    def parse_movie_catalog_page(self, response, **kwargs):
        data_state = response.xpath('//noscript[@id="react-data"]/@data-state').get()
        loaded = json.loads(data_state)

        for band in loaded['bands']:
            data = band['data']
            if data and 'type' in data and data['type'] == kwargs['type']:
                for movie in data['entries']:
                    if 'moviePageUrl' not in movie:
                        for req in self.parse_movie_from_catalog(movie):
                            yield req
                    else:
                        yield scrapy.Request('https://www.hbo.com{}'.format(movie['moviePageUrl']),
                                             callback=self.parse_movie)

    def parse_movie_from_catalog(self, data):
        item = HboItem(
            id=None,
            externalId=None,
            title=data['title'] if 'title' in data else None,
            description=data['synopsis'] if 'synopsis' in data else None,
            itemType='movie',
            network='hbo',
            goUrl=None,
            nowUrl=None
        )

        yield scrapy.Request(
            'https://proxy-v4.cms.hbo.com/v1/schedule/programs?productIds={}'.format(data['streamingId']['id']),
            callback=self.finish_parse_movie,
            meta={'item': item})

    def parse_series_from_catalog(self, data):
        item = HboItem(
            id=None,
            externalId=None,
            title=data['title'] if 'title' in data else None,
            description=data['synopsis'] if 'synopsis' in data else None,
            itemType='show',
            network='hbo',
            goUrl=None,
            nowUrl=None
        )

        yield scrapy.Request(
            'https://proxy-v4.cms.hbo.com/v1/schedule/programs?productIds={}'.format(data['streamingId']['id']),
            callback=self.finish_parse_series,
            meta={'item': item})

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
            if not movie_data:
                movie_data = _get_page_schema_with_type(react_data=loaded, typ='documentary')
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
