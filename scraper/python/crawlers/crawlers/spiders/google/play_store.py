import scrapy

from crawlers.base_spider import BaseSitemapSpider
from urllib import parse


class GooglePlayStoreSpider(BaseSitemapSpider):
    name = 'play_store'
    allowed_domains = ['play.google.com']

    sitemap_urls = [
        "https://play.google.com/robots.txt"
    ]

    sitemap_rules = [
        ('/movies/', 'parse_movie'),
        # ('/tv/show/', 'parse_show')
    ]

    # def parse_show(self, response):
    #

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
                self.log('Rent: description = {}, price = {}'.format(description, price))
                if 'plays in hd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='rent',
                            price=price.strip(),
                            quality='hd'
                        )
                    )
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='rent',
                            price=price.strip(),
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
                if 'plays in hd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='buy',
                            price=price.strip(),
                            quality='hd'
                        )
                    )
                elif 'plays in sd' in description.lower():
                    item_offers.append(
                        GooglePlayStoreItemOffer(
                            offerType='buy',
                            price=price.strip(),
                            quality='sd'
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


class GooglePlayStoreItem(scrapy.Item):
    id = scrapy.Field()
    title = scrapy.Field()
    releaseYear = scrapy.Field()
    description = scrapy.Field()
    externalId = scrapy.Field()
    # seasons = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    # premiereDate = scrapy.Field()
    # episodes = scrapy.Field()
    # additionalServiceRequired = scrapy.Field()
    posterImageUrl = scrapy.Field()
    offers = scrapy.Field()


class GooglePlayStoreItemOffer(scrapy.Item):
    offerType = scrapy.Field()
    price = scrapy.Field()
    quality = scrapy.Field()
