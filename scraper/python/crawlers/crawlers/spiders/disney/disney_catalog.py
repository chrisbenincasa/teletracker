import uuid

import scrapy
import re
import json

from crawlers.base_spider import BaseSitemapSpider
from scrapy.http.request.json_request import JsonRequest
from jsonpath_ng.ext import parse

preloaded_state_re = r'.*__PRELOADED_STATE__\s*=\s*(.*);.*'


class DisneyPlusCatalogSpider(BaseSitemapSpider):
    name = 'disneyplus'
    allowed_domains = ['disneyplus.com', 'cde-lumiere-disneyplus.bamgrid.com', 'global.edge.bamgrid.com',
                       'search-api-disney.svcs.dssott.com']

    sitemap_urls = [
        "https://www.disneyplus.com/sitemap.xml"
    ]

    sitemap_rules = [
        ('https://www.disneyplus.com/series/', 'parse_series'),
        ('https://www.disneyplus.com/movies/', 'parse_movie')
    ]

    access_token = None

    def start_requests(self):
        yield scrapy.FormRequest(url='https://global.edge.bamgrid.com/token',
                                 method='POST',
                                 headers={
                                     'Authorization': 'Bearer {}'.format(auth_token),
                                     'content-type': 'application/x-www-form-urlencoded',
                                     'Accept': 'application/json',
                                     'Accept-Language': 'en-US,en;q=0.5',
                                 },
                                 formdata={
                                     'grant_type': 'urn:ietf:params:oauth:grant-type:token-exchange',
                                     'subject_token': subject_token,
                                     'subject_token_type': 'urn:bamtech:params:oauth:token-type:device'
                                 },
                                 callback=self.set_token_and_start_scrape,
                                 errback=super().start_requests())

    def set_token_and_start_scrape(self, response):
        self.access_token = json.loads(response.text)['access_token']
        for url in super().start_requests():
            yield url

    def parse_series(self, response):
        yield self._parse_item(response, 'show')

    def parse_movie(self, response):
        yield self._parse_item(response, 'movie')

    def _parse_item(self, response, typ):
        item_id = response.url.split('/')[-1]
        scripts = response.xpath('//script/text()').getall()
        for script in scripts:
            matches = re.search(preloaded_state_re, script, re.MULTILINE)
            if matches:
                loaded = json.loads(matches.group(1))
                item = loaded['details'][item_id]
                asset_id = item['contentId']
                title = ''
                slug = ''
                for text in item['texts']:
                    if text['field'] == 'title' and text['language'] == 'en':
                        if text['type'] == 'full':
                            title = text['content']
                        elif text['type'] == 'slug':
                            slug = text['content']

                release = item['releases'][0] if 'releases' in item and len(
                    item['releases']) > 0 else None

                poster_image = None
                assets = loaded['assets'][asset_id]
                if assets and assets['images']:
                    for image in assets['images']:
                        if image['aspectRatio'] > 0 and image['aspectRatio'] < 1.0 and image['purpose'] == 'tile':
                            poster_image = image['url']
                            break

                catalog_item = DisneyPlusCatalogItem(id=item_id, title=title, slug=slug,
                                                     description=item['description'], itemType=typ,
                                                     releaseDate=release['releaseDate'] if release else None,
                                                     releaseYear=release['releaseYear'] if release else None,
                                                     url=response.url, posterImageUrl=poster_image)

                if self.access_token or self.settings.attributes.get('access_token'):
                    query = {
                        'preferredLanguage': ['en'],
                        'familyId': item_id,
                        'contentTransactionId': str(uuid.uuid4())
                    }

                    token = self.access_token if self.access_token else self.settings.attributes.get(
                        'access_token').value
                    url_fmt = 'https://search-api-disney.svcs.dssott.com' \
                              '/svc/search/v2/graphql/persisted/query/core/DmcVideoBundle?variables={}'
                    return JsonRequest(url=url_fmt.format(json.dumps(query)),
                                       headers={'Authorization': 'Bearer {}'.format(token),
                                                'Accept': 'application/json',
                                                'Accept-Language': 'en-US,en;q=0.5'},
                                       meta={'item': catalog_item},
                                       callback=self._finish_parsing_item,
                                       errback=lambda failure, item=catalog_item: self.return_item(item))
                else:
                    return catalog_item

    def return_item(self, item):
        yield item

    def _finish_parsing_item(self, response):
        loaded = json.loads(response.text)
        catalog_item = response.meta['item']
        catalog_item['cast'] = [
            DisneyPlusCatalogCastMember(name=match.value['displayName'],
                                        character=match.value['characterDetails']['character'],
                                        order=match.value['order']) for
            match in
            parse('$.data.DmcVideoBundle.video.participants[?(@.role == "Actor")]').find(loaded)]

        catalog_item['crew'] = [
            DisneyPlusCatalogCrewMember(name=match.value['displayName'],
                                        role=match.value['role'].lower(),
                                        order=match.value['order']) for
            match in
            parse('$.data.DmcVideoBundle.video.participants[?(@.role != "Actor")]').find(loaded)]

        yield catalog_item


auth_token = 'ZGlzbmV5JmJyb3dzZXImMS4wLjA.Cu56AgSfBTDag5NiRA81oLHkDZfu5L3CKadnefEAY84'

subject_token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9." \
                "eyJzdWIiOiJlYjM1NDVhZS1mYzVjLTQ2NmQtO" \
                "DZhMC00MDhhMTcyY2I4NTYiLCJhdWQiOiJ1cm" \
                "46YmFtdGVjaDpzZXJ2aWNlOnRva2VuIiwibmJ" \
                "mIjoxNTkyMDU5MDEwLCJpc3MiOiJ1cm46YmFt" \
                "dGVjaDpzZXJ2aWNlOmRldmljZSIsImV4cCI6M" \
                "jQ1NjA1OTAxMCwiaWF0IjoxNTkyMDU5MDEwLC" \
                "JqdGkiOiJmNmFkOTQ5Ni02YzRkLTQwOGItODU" \
                "5My0zMTdhMzRmZWE2NGMifQ.MB7kqTec5DhFF" \
                "2B4QdkU_EVaUwcROqiZvuMnIEHsGpEoKUaFWv" \
                "L-sYZ6pc4g4FWxnwEaU-Orm4RKSUeZw-EVew"


class DisneyPlusCatalogItem(scrapy.Item):
    id = scrapy.Field()
    title = scrapy.Field()
    slug = scrapy.Field()
    description = scrapy.Field()
    itemType = scrapy.Field()
    releaseDate = scrapy.Field()
    releaseYear = scrapy.Field()
    url = scrapy.Field()
    posterImageUrl = scrapy.Field()
    network = 'disneyplus'
    cast = scrapy.Field()
    crew = scrapy.Field()


class DisneyPlusCatalogCastMember(scrapy.Item):
    name = scrapy.Field()
    character = scrapy.Field()
    order = scrapy.Field()


class DisneyPlusCatalogCrewMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()
