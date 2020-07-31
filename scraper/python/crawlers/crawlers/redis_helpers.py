import logging

from scrapy import Spider
from scrapy.exceptions import CloseSpider
from scrapy.exceptions import DontCloseSpider
from scrapy_redis.spiders import RedisMixin
from twisted.internet import defer


class CustomRedisMixin(Spider, RedisMixin):
    def __init__(self, name=None, **kwargs):
        super().__init__(name, **kwargs)
        self.idle_df = None
        self.should_close = False
        self.close_reason = None

    # Special parse handler that cancels any deferred close signals that may be active
    # or closes the spider appropriately
    def parse(self, response):
        if self.should_close:
            raise CloseSpider(reason=self.close_reason or 'cancelled')
        else:
            # If we got a response, cancel any existing close timeouts
            if self.idle_df:
                self.idle_df.cancel()
            return super().parse(response)

    # Special idle handler for Redis spiders. Usually, Redis spiders will just run forever, because they
    # don't know when they're done. This idle handler sets a timeout to wait for new messages that other
    # workers may queue. If the timeout is reached without any new work coming in, the spider is closed.
    def spider_idle(self):
        from twisted.internet import reactor

        self.log(f'Spider went idle', logging.INFO)

        def close_self(value, _):
            self.log('Got idle timeout...closing spider', logging.INFO)
            self.crawler.engine.close_spider(self, 'finished')

        req_found = False
        for req in self.next_requests():
            req_found = True
            self.crawler.engine.crawl(req, spider=self)

        # Received the idle signal again, but found new requests. Cancel the idle
        if req_found and self.idle_df:
            self.log(f'Found more requests, canceling close deferred.', logging.INFO)
            self.idle_df.cancel()
            self.idle_df = None
        elif not req_found and not self.idle_df:
            timeout = self.settings.getint('REDIS_SPIDER_IDLE_TIMEOUT', default=300)
            self.log(f'No requests found, starting {timeout} second timeout to close.', logging.INFO)
            # We found no new requests, start the timer
            self.idle_df = defer.Deferred()
            # Wait 5 minutes for new messages and then declare that we're finished
            self.idle_df.addTimeout(timeout, reactor, onTimeoutCancel=close_self)
        elif not req_found and self.idle_df:
            self.log('No requests found. Timeout to close already started.')

        # Found more requests, keep going
        raise DontCloseSpider
