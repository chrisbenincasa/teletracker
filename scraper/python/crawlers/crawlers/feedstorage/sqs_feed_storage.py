import itertools
from collections import deque
from urllib.parse import urlparse

import boto3
from scrapy.extensions.feedexport import BlockingFeedStorage

from crawlers.util.aws import get_boto3_endpoint_url


def _chunk(l, n):
    # looping till length l
    for i in range(0, len(l), n):
        yield list(itertools.islice(l, i, i + n))


class SqsFeedStorage(BlockingFeedStorage):
    def __init__(self, uri, access_key=None, secret_key=None, region=None):
        # Parse the form sqs://sqs.us-west-2.amazonaws.com/302782651551/teletracker-es-ingest-qa.fifo
        u = urlparse(uri)._replace(scheme='https')
        self.queue_url = u.geturl()
        self.queue_name = u.path[1:].split('/')[-1]
        self.deck = deque()
        self.sqs_client = boto3.client('sqs', region_name=region, aws_access_key_id=access_key,
                                       aws_secret_access_key=secret_key, endpoint_url=get_boto3_endpoint_url())

    @classmethod
    def from_crawler(cls, crawler, uri):
        return cls(
            uri=uri,
            access_key=crawler.settings['AWS_ACCESS_KEY_ID'],
            secret_key=crawler.settings['AWS_SECRET_ACCESS_KEY'],
            region=crawler.settings['AWS_REGION'],
        )

    def open(self, spider):
        return self.deck

    def _store_in_thread(self, file):
        for group in _chunk(self.deck, 10):
            self.sqs_client.send_message_batch(
                QueueUrl=self.queue_url,
                Entries=group
            )
