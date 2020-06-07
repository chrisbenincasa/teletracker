import scrapy
import re
import json

from crawlers.base_spider import BaseSitemapSpider

preloaded_state_re = r'.*__PRELOADED_STATE__\s*=\s*(.*);.*'


class DisneyPlusCatalogSpider(BaseSitemapSpider):
    name = 'disneyplus'
    allowed_domains = ['disneyplus.com', 'cde-lumiere-disneyplus.bamgrid.com']

    sitemap_urls = [
        "https://www.disneyplus.com/sitemap.xml"
    ]

    sitemap_rules = [
        ('https://www.disneyplus.com/series/', 'parse_series'),
        ('https://www.disneyplus.com/movies/', 'parse_movie')
    ]

    def parse_series(self, response):
        yield self._parse_item(response, 'show')

    def parse_movie(self, response):
        yield self._parse_item(response, 'movie')

    def _parse_item(self, response, typ):
        id = response.url.split('/')[-1]
        scripts = response.xpath('//script/text()').getall()
        for script in scripts:
            matches = re.search(preloaded_state_re, script, re.MULTILINE)
            if matches:
                loaded = json.loads(matches.group(1))
                item = loaded['details'][id]
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

                return DisneyPlusCatalogItem(
                    id=id,
                    title=title,
                    slug=slug,
                    description=item['description'],
                    itemType=typ,
                    releaseDate=release['releaseDate'] if release else None,
                    releaseYear=release['releaseYear'] if release else None,
                    url=response.url,
                    posterImageUrl=poster_image
                )


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
