import scrapy

from datetime import datetime

from scrapy.spiders import Rule
from scrapy.linkextractors import LinkExtractor

from crawlers.base_spider import BaseCrawlSpider


class ItunesSitemapSpider(BaseCrawlSpider):
    name = 'itunes'
    allowed_domains = ['apple.com']

    start_urls = [
        'https://itunes.apple.com/us/genre/movies/id33',
    ]

    rules = (
        Rule(LinkExtractor(allow=(r'(https?://itunes.apple.com)?/us/movie',)),
             callback='parse_item', follow=True),
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

    def parse_item(self, response):
        self.log('Parse movie: {}'.format(response.url))


    def _valid_loc(self, loc):
        return 'audiobooks' not in loc and 'itunes_artist' not in loc and 'apps.apple.com' not in loc