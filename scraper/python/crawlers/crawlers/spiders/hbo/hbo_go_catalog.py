import json

from scrapy.http import JsonRequest
from jsonpath_ng.ext import parse
from crawlers.base_spider import BaseSpider
from crawlers.items import HboItem, HboCastMember, HboCrewMember

_client_id = 'dd80d19d-61e3-4052-ba5b-e38e19dd1eb6'
_scope = 'browse video_playback_free'
_grant_type = 'client_credentials'
_device_serial_number = 'b564374b-fea1-42e5-d1a7-02c66cb45d21'

CONTENT_API_URL = 'https://comet.api.hbo.com/content'

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
                crew.append(HboCrewMember(
                    name=director['person'], order=idx, role='Director'))
        if 'producers' in payload['credits']:
            for (idx, producer) in enumerate(payload['credits']['producers']):
                crew.append(HboCrewMember(
                    name=producer['person'], order=idx, role=producer['role']))
        if 'writers' in payload['credits']:
            for (idx, writer) in enumerate(payload['credits']['writers']):
                crew.append(HboCrewMember(
                    name=writer['person'], order=idx, role=writer['role']))
    return crew


class HboGoCrawlSpider(BaseSpider):
    name = 'hbo_go_catalog'
    allowed_domains = ['hbogo.com', 'comet.api.hbo.com']

    token = None

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

        yield self._make_seed_req('movies-all', self.schedule_types)
        yield self._make_seed_req('series-all', self.schedule_types)
        yield self._make_seed_req('documentaries-a-z', self.schedule_types)
        yield self._make_seed_req('comedies-a-z', self.schedule_types)
        yield self._make_seed_req('kids-a-z', self.schedule_types)

    def _make_seed_req(self, typ, cb):
        return JsonRequest(url=CONTENT_API_URL,
                           headers={
                               'Authorization': 'Bearer {}'.format(self.token)
                           },
                           method='POST',
                           data=[{'id': 'urn:hbo:grid:{}'.format(typ)}],
                           callback=cb,
                           dont_filter=True)

    def schedule_types(self, response):
        responses = json.loads(response.text)
        # Ex input. urn:hbo:tile:GWeTdEADi3LWurAEAAAIg:type:feature
        # Ex output: urn:hbo:feature:GXWVCLAy0HYWtLQEAAAjX
        for tile_urn in parse('$.[0].body.references.items[*]').find(responses):
            parts = tile_urn.value.split(':')
            urn_id = parts[3]
            typ = parts[-1]
            urn = HBO_URN_FMT.format(typ, urn_id)
            yield JsonRequest(
                url=CONTENT_API_URL,
                data=[{'id': urn}],
                method='POST',
                headers={
                    'Authorization': 'Bearer {}'.format(self.token)
                },
                callback=self.handle_movie if typ == 'feature' else self.handle_series,
                meta={'id': urn},
                dont_filter=True)

    def handle_movie(self, response):
        full_response = json.loads(response.text)
        self.log(
            'Processing https://play.hbogo.com/feature/{}'.format(response.meta['id']))
        for r in full_response:
            if r['id'] == response.meta['id']:
                payload = r['body']
                return HboItem(
                    id=response.meta['id'],
                    title=payload['titles']['full'],
                    externalId=response.meta['id'],
                    description=payload['summaries']['full'],
                    itemType='movie',
                    network='hbo',
                    goUrl='https://play.hbogo.com/feature/{}'.format(
                        response.meta['id']),
                    releaseYear=payload['releaseYear'],
                    highDef=True,
                    cast=_parse_cast(payload),
                    crew=_parse_crew(payload),
                    runtime=payload['duration']
                )

    def handle_series(self, response):
        self.log(
            'Processing https://play.hbogo.com/series/{}'.format(response.meta['id']))
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

                item = HboItem(
                    id=response.meta['id'],
                    title=payload['titles']['full'],
                    externalId=response.meta['id'],
                    description=payload['summaries']['full'],
                    itemType='show',
                    network='hbo',
                    goUrl='https://play.hbogo.com/series/{}'.format(
                        response.meta['id']),
                    highDef=True
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
