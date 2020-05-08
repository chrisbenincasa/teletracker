# -*- coding: utf-8 -*-
import scrapy
import json
import re

from crawlers.items import HuluItem, HuluEpisodeItem

additional_network_re = re.compile(r'Add\s([A-z]+).\s')


class HuluSpider(scrapy.spiders.SitemapSpider):
    name = 'hulu'
    allowed_domains = ['hulu.com']

    sitemap_urls = [
        "https://www.hulu.com/sitemap_index.xml"
    ]

    sitemap_rules = [
        ('/series/', 'parse_series'),
        ('/movie/', 'parse_movie')
    ]

    custom_settings = {
        'DOWNLOAD_DELAY': 0.1
    }

    def parse(self, response):
        pass

    def parse_series(self, response):
        data = response.xpath('//script/text()').re(r'\s*__NEXT_DATA__\s*=\s*(.*)')
        if len(data) > 0:
            loaded_data = json.loads(data[0])
            try:
                components = loaded_data["props"]["pageProps"]["layout"]["components"]
                if components:
                    entity = next((i for i in components if 'entityType' in i.keys() and i['entityType'] == 'series'),
                                  None)
                    if entity:
                        tabs = next((i for i in components if
                                     'type' in i.keys() and i["type"] == "collection_tabs"), None)

                        episodes = self._extract_episodes(tabs['tabs']) if tabs else []

                        return HuluItem(
                            id=entity['entityId'],
                            externalId=entity['entityId'],
                            title=entity['title'],
                            description=entity['description'],
                            network='Hulu',
                            itemType='show',
                            premiereDate=entity['premiereDate'],
                            episodes=episodes,
                            additionalServiceRequired=self._extract_additional_service(components)
                        )

            except KeyError as e:
                self.log('Key error: {}'.format(e))
                return

    def _extract_additional_service(self, components):
        belt = next((i for i in components if i['type'] == 'belt'), None)
        if belt and 'description' in belt:
            match = additional_network_re.search(belt['description'])
            if match:
                return match.group(1).lower()

    def _extract_episodes(self, tabs):
        episodes = []
        if tabs:
            for tab in tabs:
                try:
                    if tab['model']['type'] == 'episode_collection':
                        items = tab['model']['collection']['items']
                        for item in items:
                            episodes.append(
                                HuluEpisodeItem(
                                    id=item['id'],
                                    externalId=item['id'],
                                    genres=item['genres'],
                                    description=item['description'],
                                    title=item['name'],
                                    rating=item['rating'],
                                    episodeNumber=item['number'],
                                    seasonNumber=item['season'],
                                    premiereDate=item['premiereDate'],
                                    duration=item['duration']
                                )
                            )

                except KeyError as e:
                    self.log('continue {}'.format(e))
                    continue

        return episodes

    def parse_movie(self, response):
        data = response.xpath('//script/text()').re(r'\s*__NEXT_DATA__\s*=\s*(.*)')
        if len(data) > 0:
            loaded_data = json.loads(data[0])
            try:
                components = loaded_data["props"]["pageProps"]["layout"]["components"]
                if components:
                    for component in components:
                        if 'entityType' in component.keys() and component["entityType"] == "movie":
                            return HuluItem(
                                id=component['entityId'],
                                externalId=component['entityId'],
                                title=component['title'],
                                description=component['description'],
                                network='Hulu',
                                itemType='movie',
                                premiereDate=component['premiereDate']
                            )
            except KeyError as e:
                self.log('{}'.format(e))
                return
