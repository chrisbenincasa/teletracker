# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: https://docs.scrapy.org/en/latest/topics/item-pipeline.html
from scrapy.exceptions import DropItem

from crawlers.base_spider import VersionedSpider


class DupeIdFilterPipeline:
    def __init__(self):
        self.seen = set()

    def process_item(self, item, spider):
        if 'id' in item:
            if not item['id'] in self.seen:
                self.seen.add(item['id'])
                return item
            raise DropItem
        else:
            return item


class ItemVersioner:
    def process_item(self, item, spider):
        if isinstance(spider, VersionedSpider):
            item['version'] = spider.version
