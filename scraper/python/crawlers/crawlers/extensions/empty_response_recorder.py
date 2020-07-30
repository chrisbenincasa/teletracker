import base64
import logging

import boto3
from scrapy.exceptions import NotConfigured
from scrapy.http import Response
from twisted.internet import threads

from crawlers.spiders.common_settings import get_data_bucket

logger = logging.getLogger(__name__)

ENABLED_SETTING = 'EMPTY_RESPONSE_RECORDER_ENABLED'
OUTPUT_PREFIX_SETTING = 'EMPTY_RESPONSE_OUTPUT_PREFIX'

empty_item_signal = object()


class EmptyResponseRecorder:
    def __init__(self, bucket, output_prefix=None):
        self._s3_bucket = boto3.resource('s3').Bucket(bucket)
        self.output_prefix = output_prefix
        pass

    @classmethod
    def from_crawler(cls, crawler):
        is_enabled = crawler.settings.getbool(ENABLED_SETTING)

        if not is_enabled:
            raise NotConfigured
        elif not crawler.settings.get(OUTPUT_PREFIX_SETTING):
            logger.warning('Must define {} setting if enabled'.format(OUTPUT_PREFIX_SETTING))
            raise NotConfigured

        bucket = get_data_bucket(crawler.settings)
        output_prefix = crawler.settings.get(
            OUTPUT_PREFIX_SETTING).value if OUTPUT_PREFIX_SETTING in crawler.settings else None

        ext = cls(bucket, output_prefix)

        crawler.signals.connect(ext.empty_item, signal=empty_item_signal)

        return ext

    def empty_item(self, response: Response):
        if self.output_prefix:
            logger.info(f'Got empty item for url: {response.url}. Uploading page result.')
            url_key = base64.b64encode(response.url.encode('utf-8')).decode(encoding='utf-8')
            key = f'{self.output_prefix}/{url_key}'
            d = threads.deferToThread(lambda: self._s3_bucket.put_object(Body=response.body, Key=key))
            d.addCallback(self._handle_upload_success)
            d.addErrback(self._handle_upload_error)
        else:
            logger.info(f'Got empty item for url: {response.url}')

    def _handle_upload_success(self):
        logger.debug('Successfully uploaded object')

    def _handle_upload_error(self, error):
        logger.error(f'Error uploading: {error}')
