import json
import logging
import pathlib
import time
from urllib import parse as urlparse

import boto3
from scrapy import signals
from scrapy.exceptions import NotConfigured

logger = logging.getLogger(__name__)


class DynamoCrawlRecorder:
    def __init__(self, table_name):
        self.time_opened = 0
        self.dynamo_table = boto3.resource('dynamodb').Table(table_name)
        self.version = int(time.time())
        self.items_scraped = 0

    @classmethod
    def from_crawler(cls, crawler):
        if not crawler.settings.getbool('DYNAMO_CRAWL_TRACK_ENABLED') or not crawler.settings.get(
                'DYNAMO_CRAWL_TRACK_TABLE'):
            if not crawler.settings.get('DYNAMO_CRAWL_TRACK_ENABLED'):
                logger.warning('Must define DYNAMO_CRAWL_TRACK_TABLE setting if DYNAMO_CRAWL_TRACK_ENABLED is true')
            raise NotConfigured

        table_name = crawler.settings.get('DYNAMO_CRAWL_TRACK_TABLE')

        ext = cls(table_name)

        crawler.signals.connect(ext.spider_opened, signal=signals.spider_opened)
        crawler.signals.connect(ext.spider_closed, signal=signals.spider_closed)
        crawler.signals.connect(ext.item_scraped, signal=signals.item_scraped)

        return ext

    def spider_opened(self, spider):
        self.time_opened = int(time.time())

        self.dynamo_table.put_item(
            Item={
                'spider': spider.name,
                'version': self.version,
                'time_opened': self.time_opened,
                'metadata': self._build_metadata_blob(spider)
            }
        )
        logger.info("opened spider!!! %s", spider.settings.getdict('FEEDS'))

    def spider_closed(self, spider):
        self.dynamo_table.update_item(
            Key={
                'spider': spider.name,
                'version': self.version
            },
            UpdateExpression='SET time_closed = :tc, total_items_scraped = :tic',
            ExpressionAttributeValues={
                ':tc': int(time.time()),
                ':tic': self.items_scraped
            }
        )

        logger.info("closed spider!!! %s", spider.name)

    def item_scraped(self):
        self.items_scraped += 1

    def _build_metadata_blob(self, spider):
        metadata = {}
        outputs = []
        feeds = spider.settings.getdict('FEEDS')
        if feeds:
            for key, value in feeds.items():
                if isinstance(key, pathlib.Path):
                    full_path = 'file://{}'.format(str(key.absolute()))
                else:
                    parsed = urlparse.urlparse(key)
                    # Have something in the form xyz://123
                    if parsed.scheme:
                        full_path = key
                    else:
                        full_path = 'file://{}'.format(str(pathlib.Path(key).absolute()))

                outputs.append({
                    full_path: value
                })

        metadata['outputs'] = outputs

        return json.dumps(metadata)
