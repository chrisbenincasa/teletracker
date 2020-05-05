# -*- coding: utf-8 -*-

# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: https://docs.scrapy.org/en/latest/topics/item-pipeline.html
from scrapy.exceptions import DropItem
from scrapy.exporters import JsonLinesItemExporter


class DupeIdFilterPipeline:
    def __init__(self):
        f = open('netflix_ids.json', 'wb')
        self.exporter = JsonLinesItemExporter(f)
        self.seen = set()

    def close_spider(self, spider):
        self.exporter.finish_exporting()

    def process_item(self, item, spider):
        if not item['id'] in self.seen:
            self.seen.add(item['id'])
            return item
        raise DropItem
