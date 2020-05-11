import json
import logging
from datetime import datetime

import scrapy
from pythonjsonlogger import jsonlogger


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
            log_record['level'] = logging.getLevelName(logging.INFO)
        else:
            log_record['level'] = log_record.get('level').upper()


class BaseSitemapSpider(scrapy.spiders.SitemapSpider):
    def __init__(self, name=None, **kwargs):
        super().__init__(name, **kwargs)
        logger = logging.getLogger()
        log_handler = logging.StreamHandler()
        formatter = CustomJsonFormatter(json_default=json_translate, json_encoder=json.JSONEncoder)
        log_handler.setFormatter(formatter)
        logger.addHandler(log_handler)
