import json
import logging
import re
from datetime import datetime
from json import JSONDecodeError

import boto3
from jsonpath_ng.ext import parse
from scrapy import Request
from scrapy.exceptions import CloseSpider
from scrapy.linkextractors import LinkExtractor
from scrapy.spiders import Rule
from scrapy_redis.spiders import RedisMixin

from crawlers.base_spider import BaseCrawlSpider
from crawlers.extensions.empty_response_recorder import empty_item_signal
from crawlers.items import AmazonCastMember
from crawlers.items import AmazonCrewMember
from crawlers.items import AmazonItem
from crawlers.items import AmazonItemOffer
from crawlers.redis_helpers import CustomRedisMixin
from crawlers.settings import EXTENSIONS
from crawlers.settings import ITEM_PIPELINES
from crawlers.spiders.common_settings import DISTRIBUTED_SETTINGS
from crawlers.spiders.common_settings import get_data_bucket

select_sql = """
SELECT s."type", s.external_ids, s.id FROM s3object[*]._source s
"""

PRICE_RE = r'\$(\d+\.\d+)'

AMAZON_TV_SHOW_ENTITY_TYPE = 'TV Show'
AMAZON_MOVIE_ENTITY_TYPE = 'Movie'


class AmazonSpider(BaseCrawlSpider):
    name = 'amazon'
    store_name = 'amazon'
    allowed_domains = ['amazon.com', 'imdb.com']

    start_urls = [
        'https://www.amazon.com/gp/video/storefront'
    ]

    rules = (
        Rule(LinkExtractor(allow=(r'(https://www.amazon.com)?/gp/video/detail/.*',)),
             callback='_handle_amazon_page', follow=True),
        Rule(LinkExtractor(
            allow=r'(https://www\.amazon\.com)?/gp/video/search/ref=[A-z_]+\?phrase=[A-z0-9%]+&'), follow=True),
        Rule(LinkExtractor(
            allow=r'(https://www\.amazon\.com)?/gp/video/storefront/?.*'), follow=True)
    )

    def __init__(self, json_logging=True, *a, **kw):
        super().__init__(json_logging, *a, **kw)
        self._s3_client = boto3.client('s3')

    def start_requests(self):
        if self.settings.getbool('SEED_FROM_DUMP'):
            paginator = self._s3_client.get_paginator('list_objects_v2')

            self.log('Looking up latest ES dump.')

            page_it = paginator.paginate(
                Bucket=get_data_bucket(self.settings),
                Prefix='elasticsearch/items/',  # Filter this down further to limit results...maybe by year?
                Delimiter='/'
            )

            latest_prefix = None
            for page in page_it:
                latest_prefix = page['CommonPrefixes'][-1]['Prefix']

            if latest_prefix:
                self.log(f'Found latest ES dump at {latest_prefix}')
                limit = -1
                ids_limit = -1

                if 'OUTPUT_FILE_HANDLE_LIMIT' in self.settings:
                    limit = self.settings.getint('OUTPUT_FILE_HANDLE_LIMIT')

                if 'IDS_HANDLE_LIMIT' in self.settings:
                    ids_limit = self.settings.getint('IDS_HANDLE_LIMIT')

                total_handled = 0
                imdb_ids_handled = 0
                for page in paginator.paginate(
                        Bucket=get_data_bucket(self.settings),
                        Prefix=latest_prefix):
                    if (0 < limit <= total_handled) or (0 < ids_limit <= imdb_ids_handled):
                        break
                    for result in page['Contents']:
                        if (0 < limit <= total_handled) or (0 < ids_limit <= imdb_ids_handled):
                            break
                        total_handled += 1
                        for (tt_id, imdb_id) in self._handle_s3_select(result['Key']):
                            if (0 < limit <= total_handled) or (0 < ids_limit <= imdb_ids_handled):
                                break
                            raw_id = imdb_id.lstrip('imdb__')
                            if len(raw_id) > 0:
                                self.log(f'TT id: {tt_id}, IMDB ID: {imdb_id}', level=logging.DEBUG)
                                imdb_ids_handled += 1
                                if imdb_ids_handled % 1000 == 0:
                                    self.log(f'Handled {imdb_ids_handled} so far')
                                yield Request(f'https://www.imdb.com/title/{raw_id}/', dont_filter=True,
                                              callback=self._handle_imdb_page, meta={'id': tt_id, 'imdb_id': raw_id})
                self.log(f'Total imdb ids = {imdb_ids_handled}')
        else:
            for req in BaseCrawlSpider.start_requests(self):
                yield req

    def _handle_s3_select(self, key):
        # Query the ES dump in s3 to extract all external ids
        response = self._s3_client.select_object_content(
            Bucket='teletracker-data-us-west-2',
            Key=key,
            ExpressionType='SQL',
            Expression=select_sql,
            InputSerialization={'JSON': {'Type': 'Lines'}},
            OutputSerialization={'JSON': {}}
        )

        # Load results into a byte array. TODO: Could improve this with incremental JSON parsing
        acc = bytearray()
        for event in response['Payload']:
            if 'Records' in event:
                acc.extend(event['Records']['Payload'])

        for line in acc.decode('utf-8').split('\n'):
            if len(line) == 0:
                continue
            try:
                loaded = json.loads(line)
                if 'external_ids' in loaded:
                    # Find items that have an imdb but not an amazon id, these have not been matched yet.
                    imdb_id = next((i for i in loaded['external_ids'] if i.startswith('imdb')), None)
                    amazon_id = next((i for i in loaded['external_ids'] if i.startswith('amazon')), None)
                    if imdb_id and not amazon_id:
                        yield loaded['id'], imdb_id
            except JSONDecodeError:
                self.log(f'Failure decoding string: {line}', level=logging.WARN)
                continue

    # Extract the amazon link from the imdb page and queue the offsite link result
    def _handle_imdb_page(self, response):
        for data in response.css('.ipc-button').xpath('.//@data-ipc-data').getall():
            if 'offsite-amazon' in data:
                yield Request(f'https://www.imdb.com{data}', callback=self._handle_amazon_page,
                              meta=response.meta)

    def _handle_amazon_page(self, response):
        item = None
        for script in response.xpath('//script[@type="text/template"]/text()').getall():
            try:
                loaded = json.loads(script)
                ids = parse('$.props.state.pageTitleId').find(loaded)
                if len(ids) > 0:
                    official_id = ids[0].value
                    header_details = [detail.value for detail in
                                      parse(f'$.props.state.detail.headerDetail.{official_id}').find(loaded)]
                    if len(header_details) > 0:
                        header_detail = header_details[0]
                        item_type = 'movie' if header_detail['titleType'].lower() == 'movie' else 'show'
                        cast = []
                        crew = []
                        crew.extend([AmazonCrewMember(name=c.value['name'], order=idx, role='director') for (idx, c) in
                                     enumerate(parse('$.contributors.directors[*]').find(header_detail))])
                        crew.extend([AmazonCrewMember(name=c.value['name'], order=idx, role='producer') for (idx, c) in
                                     enumerate(parse('$.contributors.producers[*]').find(header_detail))])
                        cast.extend([AmazonCastMember(name=c.value['name'], order=idx, role='actor') for (idx, c) in
                                     enumerate(parse('$.contributors.starringActors[*]').find(header_detail))])
                        cast.extend([AmazonCastMember(name=c.value['name'], order=idx, role='actor') for (idx, c) in
                                     enumerate(parse('$.contributors.supportingActor[*]').find(header_detail))])

                        release_date = datetime.strptime(header_detail['releaseDate'],
                                                         '%B %d, %Y') if 'releaseDate' in header_detail else None

                        offers = parse(
                            f'$.props.state.action.atf.{official_id}..children[?(@.__type == "atv.wps#TvodAction")]') \
                            .find(loaded)

                        all_offers = []
                        for offer in offers:
                            try:
                                is_buy = offer.value['purchaseData']['offerType'] == 'TVOD_PURCHASE'
                                is_rent = offer.value['purchaseData']['offerType'] == 'TVOD_RENTAL'

                                price = None
                                match = re.search(PRICE_RE, offer.value['label'])
                                if match:
                                    try:
                                        price = float(match.group(1))
                                    except ValueError:
                                        continue

                                if price and is_buy or is_rent:
                                    offer_type = 'buy' if is_buy else 'rent'
                                    all_offers.append(AmazonItemOffer(offerType=offer_type, price=price, currency='USD',
                                                                      quality=offer.value['purchaseData'][
                                                                          'videoQuality']))
                                else:
                                    continue
                            except KeyError:
                                continue

                        item = AmazonItem(
                            id=official_id,
                            externalId=official_id,
                            title=header_detail['title'],
                            description=header_detail['synopsis'],
                            itemType=item_type,
                            network='amazon',
                            availableOnPrime=header_detail['isPrime'],
                            url=response.url,
                            releaseDate=release_date,
                            releaseYear=header_detail['releaseYear'],
                            cast=cast,
                            crew=crew,
                            runtime=header_detail['runtime'],
                            internalId=response.meta['id'] if 'id' in response.meta else None,
                            offers=all_offers
                        )

                        yield item
                        break
            except JSONDecodeError:
                continue

        if not item:
            self.crawler.signals.send_catch_log(empty_item_signal, response=response)


class AmazonDistributedSpider(AmazonSpider, CustomRedisMixin):
    name = 'amazon_distributed'
    custom_settings = {
        **DISTRIBUTED_SETTINGS,
        'AUTOTHROTTLE_TARGET_CONCURRENCY': 1.0,
        'EXTENSIONS': {**EXTENSIONS, 'crawlers.extensions.empty_response_recorder.EmptyResponseRecorder': 500,
                       'scrapy.extensions.closespider.CloseSpider': 100},
        'ITEM_PIPELINES': ITEM_PIPELINES,
        'ROBOTSTXT_OBEY': True
    }
    is_distributed = True

    def start_requests(self):
        for req in super().start_requests():
            yield req
        for req in RedisMixin.start_requests(self):
            yield req

    def parse(self, response):
        if self.should_close:
            raise CloseSpider(reason=self.close_reason or 'cancelled')
        else:
            # If we got a response, cancel any existing close timeouts
            if self.idle_df:
                self.idle_df.cancel()
            return super().parse(response)