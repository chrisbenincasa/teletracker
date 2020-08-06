import json
import logging
import pathlib
from datetime import datetime

import time
from urllib import parse as urlparse

import boto3
from boto3.dynamodb.conditions import And
from boto3.dynamodb.conditions import Attr
from botocore.exceptions import ClientError
from scrapy import signals
from scrapy.exceptions import NotConfigured
from scrapy.utils.conf import feed_complete_default_values_from_settings
from scrapy.utils.misc import load_object

from crawlers.util.aws import get_boto3_endpoint_url

logger = logging.getLogger(__name__)

ENABLED_SETTING = 'DYNAMO_CRAWL_TRACK_ENABLED'
DRY_MODE_SETTING = 'DYNAMO_CRAWL_TRACK_DRY_MODE'
TABLE_NAME_SETTING = 'DYNAMO_CRAWL_TRACK_TABLE'
JOIN_SPECIFIC_CRAWL_SETTING = 'DYNAMO_JOIN_SPECIFIC_CRAWL_SETTING'
JOIN_CURRENT_CRAWL_SETTING = 'DYNAMO_JOIN_CURRENT_CRAWL_SETTING'


# HACK: Copy feedexport's impl here. We need to render the feed URIs before saving their metadata
def _get_uri_params(spider, uri_params):
    params = {}
    for k in dir(spider):
        params[k] = getattr(spider, k)
    ts = datetime.utcnow().replace(microsecond=0).isoformat().replace(':', '-')
    params['time'] = ts
    uripar_function = load_object(
        uri_params) if uri_params else lambda x, y: None
    uripar_function(params, spider)
    return params


class DynamoCrawlRecorder:
    def __init__(self, table_name, dry_mode=False):
        self.time_opened = 0
        self.dynamo_table = boto3.resource(
            'dynamodb', endpoint_url=get_boto3_endpoint_url()).Table(table_name)
        self.default_version = int(time.time())
        self.dry_mode = dry_mode
        self.spider_info = dict()
        self.item_count_by_spider = dict()

    @classmethod
    def from_crawler(cls, crawler):
        is_enabled = crawler.settings.getbool(
            DRY_MODE_SETTING) or crawler.settings.getbool(ENABLED_SETTING)
        is_dry_mode = crawler.settings.getbool(DRY_MODE_SETTING)

        if not is_enabled:
            raise NotConfigured
        elif not crawler.settings.get(TABLE_NAME_SETTING):
            logger.warning(
                'Must define {} setting if enabled'.format(TABLE_NAME_SETTING))
            raise NotConfigured

        table_name = crawler.settings.get(TABLE_NAME_SETTING)

        ext = cls(table_name, dry_mode=is_dry_mode)

        crawler.signals.connect(
            ext.spider_opened, signal=signals.spider_opened)
        crawler.signals.connect(
            ext.spider_closed, signal=signals.spider_closed)
        crawler.signals.connect(ext.item_scraped, signal=signals.item_scraped)

        return ext

    def spider_opened(self, spider):
        time_opened = int(time.time())

        # Use spider's defined version, if available.
        if spider.name not in self.spider_info:
            self.spider_info[spider.name] = {
                **(self._build_spider_info(spider)), 'time_opened': time_opened}

        info = self.spider_info[spider.name]

        if not hasattr(spider, 'version'):
            setattr(spider, 'version', info['version'])
            logger.info(f'Set spider version: {spider.version}')

        if self.dry_mode:
            logger.info(
                'Dynamo DRY MODE (open): Would\'ve written item: {} to table {}'.format(self._build_item(spider, info),
                                                                                        self.dynamo_table.name))
        elif info['is_distributed'] and spider.settings.getbool(JOIN_CURRENT_CRAWL_SETTING, default=True):
            # Look for an open crawl for the same spider
            response = self.dynamo_table.query(
                KeyConditionExpression='spider = :n',
                ExpressionAttributeValues={
                    ':n': info['name']
                },
                FilterExpression=And(Attr('time_closed').not_exists(), Attr(
                    'is_distributed').eq(True)),
                ScanIndexForward=False,
                Limit=1
            )

            if len(response['Items']) == 1:
                # Distributed crawl in progress, register this spider
                item = response['Items'][0]
                version = int(item['version'])

                key = {
                    'spider': info['name'],
                    'version': version
                }

                # Update the version of the spider to the pre-existing one
                info['version'] = version
                if not hasattr(spider, 'version') or getattr(spider, 'version') != version:
                    setattr(spider, 'version', version)
                    logger.info(f'Set spider version: {spider.version}')

                logger.info(f'Joined active crawl at version {version}')

                parsed_metadata = json.loads(item['metadata'])
                metadata = self._build_metadata_blob(spider)

                merged_metadata = {
                    **parsed_metadata,
                    **metadata,
                    # Filter out duplicate canonical inputs
                    'outputs': self._unique_dict_list_by_key(metadata['outputs'] + parsed_metadata['outputs'])
                }

                self.dynamo_table.update_item(
                    Key=key,
                    UpdateExpression='SET num_open_spiders = num_open_spiders + :inc, metadata = :metadata',
                    ExpressionAttributeValues={
                        ':inc': 1,
                        ':metadata': json.dumps(merged_metadata)
                    },
                    ConditionExpression=Attr('time_closed').not_exists(),
                )
            else:
                # Start a fresh crawl
                self.dynamo_table.put_item(
                    Item=self._build_item(spider, info)
                )

        else:
            # Insert the new crawl
            self.dynamo_table.put_item(
                Item=self._build_item(spider, info)
            )

    def _build_item(self, spider, info):
        return {
            'spider': info['name'],
            'version': info['version'],
            'time_opened': info['time_opened'],
            'metadata': json.dumps(self._build_metadata_blob(spider)),
            'num_open_spiders': 1,
            'is_distributed': info['is_distributed']
        }

    def spider_closed(self, spider, reason):
        logger.info(
            f'Dynamo crawl recorder closing spider with reason: {reason}')

        if spider.name not in self.spider_info:
            self.spider_info[spider.name] = self._build_spider_info(spider)

        info = self.spider_info[spider.name]

        key = {
            'spider': info['name'],
            'version': info['version']
        }

        finalize_expression_attrs = {
            ':tc': int(time.time()),
            ':tic': self.item_count_by_spider[
                spider.name] if spider.name in self.item_count_by_spider else 0,
        }

        final_set_expr = 'SET time_closed = :tc, total_items_scraped = :tic'

        if self.dry_mode:
            logger.info(
                'Dynamo DRY MODE (close): Would\'ve updated item {}.'.format(key))
        elif info['is_distributed']:
            response = self.dynamo_table.update_item(
                Key=key,
                UpdateExpression='SET num_open_spiders = num_open_spiders - :dec',
                ExpressionAttributeValues={
                    ':dec': 1
                },
                ReturnValues='UPDATED_NEW'
            )

            if response['Attributes']['num_open_spiders'] == 0 and reason == 'finished':
                logger.info(f'Attempting to finalize crawl for key {key}')

                try:
                    self.dynamo_table.update_item(
                        Key=key,
                        UpdateExpression=final_set_expr,
                        ConditionExpression=Attr('num_open_spiders').eq(0),
                        ExpressionAttributeValues=finalize_expression_attrs
                    )
                except ClientError as error:
                    if error.response['Error']['Code'] == 'ConditionalCheckFailedException':
                        logger.warning(
                            'Another spider was opened before crawl could be closed.')
                    else:
                        raise error

        else:
            self.dynamo_table.update_item(
                Key=key,
                UpdateExpression=final_set_expr,
                ExpressionAttributeValues=finalize_expression_attrs
            )

        logger.info("closed spider!!! %s", spider.name)

    def _build_spider_info(self, spider):
        if hasattr(spider, 'version'):
            logger.debug('Got version from spider: {}'.format(spider.version))

        is_distributed = spider.is_distributed if hasattr(
            spider, 'is_distributed') else False

        spider_version = spider.version if hasattr(
            spider, 'version') else self.default_version
        spider_name = spider.store_name if hasattr(
            spider, 'store_name') else spider.name

        return dict({
            'version': spider_version,
            'name': spider_name,
            'is_distributed': is_distributed
        })

    def item_scraped(self, spider):
        if spider.name not in self.item_count_by_spider:
            self.item_count_by_spider[spider.name] = 0

        self.item_count_by_spider[spider.name] += 1

    def _unique_dict_list_by_key(self, ls):
        seen_keys = set()
        combined_outputs = []

        # Filter out duplicate canonical inputs
        for d in ls:
            for k, v in d.items():
                if k not in seen_keys:
                    seen_keys.add(k)
                    combined_outputs.append({k: v})

        return combined_outputs

    def _build_metadata_blob(self, spider):
        metadata = {}
        outputs = []
        feeds = spider.settings.getdict('FEEDS')
        if feeds:
            new_feeds = {}
            for key, value in feeds.items():
                uri = str(key)  # handle pathlib.Path objects
                new_feeds[uri] = feed_complete_default_values_from_settings(
                    value, spider.settings)

            for uri, feed in new_feeds.items():
                uri = uri % _get_uri_params(spider, feed['uri_params'])
                if isinstance(uri, pathlib.Path):
                    full_path = 'file://{}'.format(str(uri.absolute()))
                else:
                    parsed = urlparse.urlparse(uri)
                    # Have something in the form xyz://123
                    if parsed.scheme:
                        # Special case for distributed spiders, which could
                        if getattr(spider, 'is_distributed') and parsed.scheme == 's3':
                            parsed = parsed._replace(
                                path='/'.join(parsed.path.split('/')[:-1]))

                        full_path = parsed.geturl()
                    else:
                        full_path = 'file://{}'.format(
                            str(pathlib.Path(uri).absolute()))

                outputs.append({
                    full_path: feed
                })

        metadata['outputs'] = outputs

        return metadata
