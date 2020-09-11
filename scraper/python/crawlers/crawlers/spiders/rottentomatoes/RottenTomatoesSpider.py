from crawlers.base_spider import BaseSitemapSpider


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
        self.log('got thingy {}'.format(response.url))

    def parse_show(self, response):
        self.log('got tv {}'.format(response.url))