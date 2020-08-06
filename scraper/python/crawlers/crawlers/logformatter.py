
from scrapy import logformatter


class LogFormatter(logformatter.LogFormatter):
    def crawled(self, request, response, spider):
        return super().crawled(request, response, spider)

    def scraped(self, item, response, spider):
        return super().scraped(item, response, spider)

    def dropped(self, item, exception, response, spider):
        return super().dropped(item, exception, response, spider)

    def item_error(self, item, exception, response, spider):
        return super().item_error(item, exception, response, spider)

    def spider_error(self, failure, request, response, spider):
        return super().spider_error(failure, request, response, spider)

    def download_error(self, failure, request, spider, errmsg=None):
        return super().download_error(failure, request, spider, errmsg)

    @classmethod
    def from_crawler(cls, crawler):
        return super().from_crawler(crawler)
