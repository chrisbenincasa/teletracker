import scrapy

from crawlers.base_spider import BaseSitemapSpider
from urllib import parse

from crawlers.spiders.common_settings import DISTRIBUTED_SETTINGS


class GooglePlayStoreSpider(BaseSitemapSpider):
    name = 'google_play_store'
    store_name = name

    allowed_domains = ['play.google.com']

    sitemap_urls = [
        "https://play.google.com/robots.txt"
    ]

    sitemap_rules = [
        ('/movies/', 'parse_movie'),
        # ('/tv/show/', 'parse_show')
    ]

    def parse_show(self, response):
        title = response.xpath('//h1[@itemprop="name"]/span/text()').get()
        release_year = response.xpath('//h1[@itemprop="name"]/following-sibling::div//text()').re(r'\d{4}')[0]
        descriptions = response.xpath('//div[@itemprop="description"]')
        item_description = ''
        for description_node in descriptions:
            text = description_node.xpath('./span/text()').get()
            if text:
                item_description = text
                break

        seasons = response.xpath('//*[@aria-label="Seasons"]//*[@role="option"]')
        for season in seasons:
            link = season.attrib['data-value']
            yield scrapy.Request(url='https://play.google.com{}'.format(link), callback=self.parse_show_season)

    def parse_show_season(self, response):
        pass

    def parse_movie(self, response):
        title = response.xpath('//h1[@itemprop="name"]/span/text()').get()
        release_year = response.xpath('//h1[@itemprop="name"]/following-sibling::div//text()').re(r'\d{4}')[0]
        descriptions = response.xpath('//div[@itemprop="description"]')
        item_description = ''
        for description_node in descriptions:
            text = description_node.xpath('./span/text()').get()
            if text:
                item_description = text
                break

        rent_buttons = response.xpath('//button[contains(text(),"Rent")]')
        item_offers = []

        for rent_button in rent_buttons:
            offers = rent_button.xpath('.//span[@itemprop="offers"]')
            for offer in offers:
                price = offer.xpath('.//meta[@itemprop="price"]')[0].attrib['content']
                description = offer.xpath('.//meta[@itemprop="description"]')[0].attrib['content']
                parsed_price = float(price.strip().lstrip('$'))
                if 'plays in hd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='rent',
                            price=parsed_price,
                            quality='hd'
                        )
                    )
                elif 'plays in sd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='rent',
                            price=parsed_price,
                            quality='sd'
                        )
                    )

        buy_buttons = response.xpath('//button[contains(text(),"Buy")]')
        for buy_button in buy_buttons:
            offers = buy_button.xpath('.//span[@itemprop="offers"]')
            for offer in offers:
                price = offer.xpath('.//meta[@itemprop="price"]')[0].attrib['content']
                description = offer.xpath('.//meta[@itemprop="description"]')[0].attrib['content']
                self.log('Buy: description = {}, price = {}'.format(description, price))
                parsed_price = float(price.strip().lstrip('$'))
                if 'plays in hd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='buy',
                            price=parsed_price,
                            quality='hd',
                            currency='USD'
                        )
                    )
                elif 'plays in sd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='buy',
                            price=parsed_price,
                            quality='sd',
                            currency='USD'
                        )
                    )

        item_id = parse.parse_qs(parse.urlparse(response.url).query)['id'][0]
        yield GooglePlayStoreItem(
            id=item_id,
            title=title,
            releaseYear=release_year,
            description=item_description,
            externalId=item_id,
            itemType='movie',
            network='google_play_store',  # TODO is this right
            offers=item_offers
        )


class DistributedGooglePlayStoreSpider(GooglePlayStoreSpider):
    name = 'google_play_store_distributed'
    is_distributed = True
    custom_settings = {**DISTRIBUTED_SETTINGS, 'AUTOTHROTTLE_TARGET_CONCURRENCY': 8}


class GooglePlayStoreItem(scrapy.Item):
    type = 'GooglePlayStoreItem'
    id = scrapy.Field()
    title = scrapy.Field()
    releaseYear = scrapy.Field()
    description = scrapy.Field()
    externalId = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    posterImageUrl = scrapy.Field()
    offers = scrapy.Field()
    # seasons = scrapy.Field()
    # premiereDate = scrapy.Field()
    # episodes = scrapy.Field()
    # additionalServiceRequired = scrapy.Field()


class GooglePlayStoreItemOffer(scrapy.Item):
    offerType = scrapy.Field()
    price = scrapy.Field()
    quality = scrapy.Field()
    currency = scrapy.Field()
