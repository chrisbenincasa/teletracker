import os

from crawlers.settings import ITEM_PIPELINES

DATA_BUCKET = 'DATA_BUCKET'

DEFAULT_DATA_BUCKET = 'teletracker-data-us-west-2'


def get_data_bucket(settings):
    return settings.get(DATA_BUCKET).value if DATA_BUCKET in settings else DEFAULT_DATA_BUCKET


DISTRIBUTED_SETTINGS = {
    'SCHEDULER': "scrapy_redis.scheduler.Scheduler",
    'DUPEFILTER_CLASS': "scrapy_redis.dupefilter.RFPDupeFilter",
    'ITEM_PIPELINES': {**ITEM_PIPELINES, 'scrapy_redis.pipelines.RedisPipeline': 400},
    'REDIS_HOST': os.environ['REDIS_HOST']
}
