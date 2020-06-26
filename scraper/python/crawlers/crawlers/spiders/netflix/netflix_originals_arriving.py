import json

from scrapy.http import JsonRequest

from crawlers.base_spider import BaseSpider


class NetflixOriginalsArrivingSpider(BaseSpider):
    name = "netflix_originals_arriving"
    allowed_domains = ['netflix.com']

    def start_requests(self):
        yield JsonRequest(url='https://media.netflix.com/gateway/v1/en/titles/upcoming', method='GET',
                          callback=self.handle_arriving_json)

    def handle_arriving_json(self, response):
        loaded = json.loads(response.text)
