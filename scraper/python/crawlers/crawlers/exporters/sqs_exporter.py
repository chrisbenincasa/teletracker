from uuid import uuid4

from scrapy.exporters import BaseItemExporter
from scrapy.utils.serialize import ScrapyJSONEncoder


def to_sqs_message(encoded_json):
    return {
        'Id': str(uuid4()),
        'MessageBody': encoded_json
    }


class SqsItemExporter(BaseItemExporter):
    def __init__(self, spider, deck, *args, **kwargs):
        super().__init__(**kwargs)
        self._kwargs.setdefault('ensure_ascii', not self.encoding)
        self._spider_version = spider.version
        self._spider_name = spider.name
        self.encoder = ScrapyJSONEncoder(**self._kwargs)
        self.deck = deck

    @classmethod
    def from_crawler(cls, crawler, deck, *args, **kwargs):
        return cls(spider=crawler.spider, deck=deck, *args, **kwargs)

    def export_item(self, item):
        itemdict = dict(self._get_serialized_fields(item))
        whole_dict = {
            'type': item.type,
            'version': self._spider_version,
            'item': itemdict
        }
        data = self.encoder.encode(whole_dict) + '\n'
        self.deck.append(to_sqs_message(data))
