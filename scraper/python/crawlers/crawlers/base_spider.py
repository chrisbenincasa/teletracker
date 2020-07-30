import json
import logging
import scrapy

from datetime import datetime
from pythonjsonlogger import jsonlogger
from scrapy_redis.spiders import RedisMixin


def json_translate(obj):
    if isinstance(obj, scrapy.Spider):
        return obj.name


class CustomJsonFormatter(jsonlogger.JsonFormatter):
    def add_fields(self, log_record, record, message_dict):
        super(CustomJsonFormatter, self).add_fields(log_record, record, message_dict)
        if not log_record.get('timestamp'):
            # this doesn't use record.created, so it is slightly off
            now = datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%fZ')
            log_record['timestamp'] = now

        if not log_record.get('level'):
            if record.levelname:
                log_record['level'] = record.levelname
            else:
                log_record['level'] = logging.getLevelName(logging.INFO)
        else:
            log_record['level'] = log_record.get('level').upper()

class VersionedSpider:
    import time
    version = int(time.time())


class BaseSitemapSpider(scrapy.spiders.SitemapSpider, VersionedSpider):
    def __init__(self, json_logging=True, name=None, **kwargs):
        super().__init__(name, **kwargs)
        if json_logging is None or json_logging:
            logger = logging.getLogger()
            log_handler = logging.StreamHandler()
            formatter = CustomJsonFormatter(json_default=json_translate, json_encoder=json.JSONEncoder)
            log_handler.setFormatter(formatter)
            logger.addHandler(log_handler)

    @classmethod
    def from_crawler(cls, crawler, *args, **kwargs):
        settings = crawler.settings
        spider = cls(settings.getbool('JSON_LOGGING'), *args, **kwargs)
        spider._set_crawler(crawler)
        return spider


class BaseCrawlSpider(scrapy.spiders.CrawlSpider, VersionedSpider):
    def __init__(self, json_logging=True, *a, **kw):
        super().__init__(*a, **kw)
        if json_logging is None or json_logging:
            logger = logging.getLogger()
            log_handler = logging.StreamHandler()
            formatter = CustomJsonFormatter(json_default=json_translate, json_encoder=json.JSONEncoder)
            log_handler.setFormatter(formatter)
            logger.addHandler(log_handler)

    @classmethod
    def from_crawler(cls, crawler, *args, **kwargs):
        settings = crawler.settings
        spider = cls(settings.getbool('JSON_LOGGING'), *args, **kwargs)
        spider._set_crawler(crawler)
        spider._follow_links = crawler.settings.getbool('CRAWLSPIDER_FOLLOW_LINKS', True)
        if isinstance(spider, RedisMixin):
            spider.setup_redis(crawler)
        return spider


class BaseSpider(scrapy.spiders.Spider, VersionedSpider):
    def __init__(self, json_logging=True, *a, **kw):
        super().__init__(*a, **kw)
        if json_logging is None or json_logging:
            logger = logging.getLogger()
            log_handler = logging.StreamHandler()
            formatter = CustomJsonFormatter(json_default=json_translate, json_encoder=json.JSONEncoder)
            log_handler.setFormatter(formatter)
            logger.addHandler(log_handler)

    @classmethod
    def from_crawler(cls, crawler, *args, **kwargs):
        settings = crawler.settings
        spider = cls(settings.getbool('JSON_LOGGING'), *args, **kwargs)
        spider._set_crawler(crawler)
        return spider
