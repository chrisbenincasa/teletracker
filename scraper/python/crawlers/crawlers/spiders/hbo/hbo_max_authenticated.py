import json
import time

from scrapy.http import JsonRequest
from jsonpath_ng.ext import parse
from crawlers.base_spider import BaseSpider
from crawlers.items import HboItem, HboCastMember, HboCrewMember
from crawlers.spiders.hbo.hbo_max import HboMaxItem

_client_id = '585b02c8-dbe1-432f-b1bb-11cf670fbeb0'
_client_secret = '585b02c8-dbe1-432f-b1bb-11cf670fbeb0'
_scope = 'browse video_playback_free'
_grant_type = 'client_credentials'
_device_serial_number = '7de92ed7-d64c-4534-9dd8-fca4cd105262'

EXPRESS_CONTENT_API_URL_FMT = 'https://comet.api.hbo.com/express-content/{}' \
                              '?device-code=desktop&product-code=hboMax&api-version=v9&country-code=us' \
                              '&profile-type=default&signed-in=false'

HBO_URN_FMT = 'urn:hbo:{}:{}'


def _parse_cast(payload):
    cast = []
    if 'credits' in payload and 'cast' in payload['credits']:
        cast = [HboCastMember(name=member['person'], role=member['role'], order=idx) for (idx, member) in
                enumerate(payload['credits']['cast'])]
    return cast


def _parse_crew(payload):
    crew = []
    if 'credits' in payload:
        if 'directors' in payload['credits']:
            for (idx, director) in enumerate(payload['credits']['directors']):
                crew.append(HboCrewMember(name=director['person'], order=idx, role='Director'))
        if 'producers' in payload['credits']:
            for (idx, producer) in enumerate(payload['credits']['producers']):
                crew.append(HboCrewMember(name=producer['person'], order=idx, role=producer['role']))
        if 'writers' in payload['credits']:
            for (idx, writer) in enumerate(payload['credits']['writers']):
                crew.append(HboCrewMember(name=writer['person'], order=idx, role=writer['role']))
    return crew


class HboMaxAuthenticatedCrawlSpider(BaseSpider):
    name = 'hbo_max_authenticated'
    allowed_domains = ['hbomax.com', 'comet.api.hbo.com']
    token = None
    version = int(time.time())

    def start_requests(self):
        # {"client_id":"dd80d19d-61e3-4052-ba5b-e38e19dd1eb6","client_secret":"dd80d19d-61e3-4052-ba5b-e38e19dd1eb6",
        # "scope":"browse video_playback_free","grant_type":"client_credentials",
        # "deviceSerialNumber":"b564374b-fea1-42e5-d1a7-02c66cb45d21"}
        yield JsonRequest(url='https://comet.api.hbo.com/tokens', method='POST', callback=self.handle_token_response,
                          data={
                              'client_id': _client_id,
                              'client_secret': _client_id,
                              'scope': _scope,
                              'grant_type': _grant_type,
                              'deviceSerialNumber': _device_serial_number
                          })

    def handle_token_response(self, response):
        self.token = json.loads(response.text)['access_token']
        self.log('token: {}'.format(self.token))

        yield self._make_seed_req(HBO_URN_FMT.format('page', 'movies'), self.schedule_types)
        yield self._make_seed_req(HBO_URN_FMT.format('page', 'series'), self.schedule_types)
        yield self._make_seed_req(HBO_URN_FMT.format('page', 'originals'), self.schedule_types)

    def _make_seed_req(self, typ, cb):
        return JsonRequest(url=EXPRESS_CONTENT_API_URL_FMT.format(typ),
                           headers={
                               'Authorization': 'Bearer {}'.format(self.token)
                           },
                           callback=cb,
                           dont_filter=True)

    def schedule_types(self, response):
        responses = json.loads(response.text)
        grid_ids = [x.value for x in parse('$[?(@.body.label=="A-Z")].body.references.items[*]').find(responses)]
        all_ids = []
        for grid_id in grid_ids:
            all_ids.extend(
                [x.value for x in parse('$[?(@.id == "{}")].body.references.items[*]'.format(grid_id)).find(responses)])

        # Ex input. urn:hbo:tile:GWeTdEADi3LWurAEAAAIg:type:feature
        # Ex output: urn:hbo:feature:GXWVCLAy0HYWtLQEAAAjX
        for tile_urn in all_ids:
            parts = tile_urn.split(':')
            urn_id = parts[3]
            typ = parts[-1]
            urn = HBO_URN_FMT.format(typ, urn_id)
            yield JsonRequest(
                url=EXPRESS_CONTENT_API_URL_FMT.format(urn),
                headers={
                    'Authorization': 'Bearer {}'.format(self.token)
                },
                callback=self.handle_movie if typ == 'feature' else self.handle_series,
                meta={'id': urn},
                dont_filter=True)

    def handle_movie(self, response):
        full_response = json.loads(response.text)
        self.log('Processing https://play.hbomax.com/feature/{}'.format(response.meta['id']))
        for r in full_response:
            if r['id'] == response.meta['id']:
                payload = r['body']
                return HboMaxItem(
                    id=response.meta['id'],
                    title=payload['titles']['full'],
                    externalId=response.meta['id'],
                    description=payload['summaries']['full'],
                    itemType='movie',
                    network='hbo-max',
                    url='https://play.hbomax.com/feature/{}'.format(response.meta['id']),
                    releaseYear=payload['releaseYear'],
                    cast=_parse_cast(payload),
                    crew=_parse_crew(payload),
                    runtime=payload['duration'],
                    version=self.version
                )

    def handle_series(self, response):
        parts = response.meta['id'].split(':')
        typ = parts[2]

        self.log('Processing https://play.hbomax.com/{}/{}'.format(typ, response.meta['id']))
        full_response = json.loads(response.text)

        season_ref = None
        episode_ref = None
        item = None
        for r in full_response:
            if r['id'] == response.meta['id']:
                payload = r['body']
                if 'references' in payload and 'seasons' in payload['references']:
                    season_ref = payload['references']['seasons']

                if 'references' in payload and 'episodes' in payload['references']:
                    episode_ref = payload['references']['episodes'][0] if len(
                        payload['references']['episodes']) > 0 else None

                item = HboMaxItem(
                    id=response.meta['id'],
                    title=payload['titles']['full'],
                    externalId=response.meta['id'],
                    description=payload['summaries']['full'],
                    itemType='show',
                    network='hbo-max',
                    url='https://play.hbomax.com/{}/{}'.format(typ, response.meta['id']),
                    version=self.version
                )

        if item and episode_ref:
            for r in full_response:
                if r['id'] == episode_ref:
                    payload = r['body']
                    item['cast'] = _parse_cast(payload)
                    item['crew'] = _parse_crew(payload)
                    item['releaseYear'] = payload['releaseYear']
                    item['runtime'] = payload['duration']

        return item
