import json
import logging
import pathlib
import time
from urllib import parse as urlparse

import boto3
from scrapy import signals
from scrapy.exceptions import NotConfigured

logger = logging.getLogger(__name__)

ENABLED_SETTING = 'DYNAMO_CRAWL_TRACK_ENABLED'
DRY_MODE_SETTING = 'DYNAMO_CRAWL_TRACK_DRY_MODE'
TABLE_NAME_SETTING = 'DYNAMO_CRAWL_TRACK_TABLE'


class DynamoCrawlRecorder:
    def __init__(self, table_name, dry_mode=False):
        self.time_opened = 0
        self.dynamo_table = boto3.resource('dynamodb').Table(table_name)
        self.version = int(time.time())
        self.items_scraped = 0
        self.dry_mode = dry_mode

    @classmethod
    def from_crawler(cls, crawler):
        is_enabled = crawler.settings.getbool(DRY_MODE_SETTING) or crawler.settings.getbool(ENABLED_SETTING)
        is_dry_mode = crawler.settings.getbool(DRY_MODE_SETTING)

        if not is_enabled:
            raise NotConfigured
        elif not crawler.settings.get(TABLE_NAME_SETTING):
            logger.warning('Must define {} setting if enabled'.format(TABLE_NAME_SETTING))
            raise NotConfigured

        table_name = crawler.settings.get(TABLE_NAME_SETTING)

        ext = cls(table_name, dry_mode=is_dry_mode)

        crawler.signals.connect(ext.spider_opened, signal=signals.spider_opened)
        crawler.signals.connect(ext.spider_closed, signal=signals.spider_closed)
        crawler.signals.connect(ext.item_scraped, signal=signals.item_scraped)

        return ext

    # TODO: This breaks if there are >1 spiders in the crawl. Keep map of spider=>version
    def spider_opened(self, spider):
        # Use spider's defined version, if available.
        if hasattr(spider, 'version'):
            logger.debug('Got version from spider: {}'.format(spider.version))
            self.version = spider.version

        self.time_opened = int(time.time())

        logger.info("opened spider!!! %s", spider.settings.getdict('FEEDS'))
        item = {
            'spider': spider.name,
            'version': self.version,
            'time_opened': self.time_opened,
            'metadata': self._build_metadata_blob(spider)
        }

        if self.dry_mode:
            logger.info(
                'Dynamo DRY MODE (open): Would\'ve written item: {} to table {}'.format(item, self.dynamo_table.name))
        else:
            self.dynamo_table.put_item(
                Item=item
            )

    def spider_closed(self, spider):
        key = {
            'spider': spider.name,
            'version': self.version
        }
        if self.dry_mode:
            logger.info('Dynamo DRY MODE (close): Would\'ve updated item {}.'.format(key))
        else:
            self.dynamo_table.update_item(
                Key=key,
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
