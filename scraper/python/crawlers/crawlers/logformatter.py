import logging

from scrapy import logformatter
from scrapy.settings import Settings


class LogFormatter(logformatter.LogFormatter):
    def __init__(self, settings: Settings):
        scraper_level = settings.get('SCRAPED_LOG_LEVEL', default=None)
        dropped_level = settings.get('DROPPED_LOG_LEVEL', default=None)
        self.scraper_quiet = settings.getbool('SCRAPED_QUIET_LOG', default=False)
        self.scraper_level = getattr(logging, scraper_level) if scraper_level else logging.DEBUG
        self.dropped_level = getattr(logging, dropped_level) if dropped_level else logging.WARN

    def crawled(self, request, response, spider):
        return super().crawled(request, response, spider)

    def scraped(self, item, response, spider):
        s = super().scraped(item, response, spider)
        s['level'] = self.scraper_level
        if self.scraper_quiet:
            s['msg'] = "Scraped from %(src)s: ID = %(id)s"
            s['args']['id'] = item['id']
            del s['args']['item']
        return s

    def dropped(self, item, exception, response, spider):
        s = super().dropped(item, exception, response, spider)
        s['level'] = self.dropped_level
        return s

    def item_error(self, item, exception, response, spider):
        return super().item_error(item, exception, response, spider)

    def spider_error(self, failure, request, response, spider):
        return super().spider_error(failure, request, response, spider)

    def download_error(self, failure, request, spider, errmsg=None):
        return super().download_error(failure, request, spider, errmsg)

    @classmethod
    def from_crawler(cls, crawler):
        return cls(settings=crawler.settings)
