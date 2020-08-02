import logging

import itertools
from collections import deque
from urllib.parse import urlparse

import boto3
from scrapy.extensions.feedexport import BlockingFeedStorage

from crawlers.util.aws import get_boto3_endpoint_url

logger = logging.getLogger(__name__)


def _chunk(l, n):
    # looping till length l
    for i in range(0, len(l), n):
        yield list(itertools.islice(l, i, i + n))


class SqsFeedStorage(BlockingFeedStorage):
    def __init__(self, uri):
        # Parse the form sqs://sqs.us-west-2.amazonaws.com/302782651551/teletracker-es-ingest-qa.fifo
        u = urlparse(uri)._replace(scheme='https')
        self.queue_url = u.geturl()
        self.queue_name = u.path[1:].split('/')[-1]
        self.writer = SqsFeedStorageWriter(queue_url=self.queue_url)

    @classmethod
    def from_crawler(cls, crawler, uri):
        return cls(uri=uri)

    def open(self, spider):
        return self.writer

    def _store_in_thread(self, file):
        self.writer.flush()


class SqsFeedStorageWriter:
    def __init__(self, queue_url, buffer_size=10, chunk_size=10):
        self.deck = deque()
        self.queue_url = queue_url
        self.buffer_size = buffer_size
        self.chunk_size = chunk_size
        self.sqs_client = boto3.client('sqs', endpoint_url=get_boto3_endpoint_url())

    def add(self, item):
        self.deck.append(item)
        if len(self.deck) >= self.buffer_size:
            self.flush(self.buffer_size)

    def flush(self, num=None):
        buf = []
        if not num:
            # Flush all
            buf = self.deck
        else:
            # Flush certain amount, thread-safe (happens mid run)
            for _ in range(0, self.buffer_size):
                buf.append(self.deck.popleft())

        logger.info(f'Enqueuing {len(buf)} items in chunks of {self.chunk_size}')

        for group in _chunk(buf, self.chunk_size):
            self.sqs_client.send_message_batch(
                QueueUrl=self.queue_url,
                Entries=group
            )
