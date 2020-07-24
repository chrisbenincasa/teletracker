# -*- coding: utf-8 -*-

# Define here the models for your scraped items
#
# See documentation in:
# https://docs.scrapy.org/en/latest/topics/items.html

import scrapy


class CrawlersItem(scrapy.Item):
    # define the fields for your item here like:
    # name = scrapy.Field()
    pass


class NetflixItem(scrapy.Item):
    type = 'NetflixItem'
    id = scrapy.Field()
    title = scrapy.Field()
    releaseYear = scrapy.Field()
    network = scrapy.Field()
    itemType = scrapy.Field()
    externalId = scrapy.Field()
    description = scrapy.Field()
    actors = scrapy.Field()
    creator = scrapy.Field()
    director = scrapy.Field()
    genres = scrapy.Field()
    contentRating = scrapy.Field()
    seasons = scrapy.Field()
    cast = scrapy.Field()
    crew = scrapy.Field()


class NetflixCastMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class NetflixCrewMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class NetflixItemSeason(scrapy.Item):
    seasonNameRaw = scrapy.Field()
    seasonNumber = scrapy.Field()
    releaseYear = scrapy.Field()
    description = scrapy.Field()
    episodes = scrapy.Field()


class NetflixItemEpisode(scrapy.Item):
    seasonNumber = scrapy.Field()
    episodeNumber = scrapy.Field()
    name = scrapy.Field()
    runtime = scrapy.Field()
    description = scrapy.Field()


class HuluItem(scrapy.Item):
    type = 'HuluItem'
    id = scrapy.Field()
    title = scrapy.Field()
    description = scrapy.Field()
    externalId = scrapy.Field()
    seasons = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    premiereDate = scrapy.Field()
    episodes = scrapy.Field()
    additionalServiceRequired = scrapy.Field()
    posterImageUrl = scrapy.Field()


class HuluEpisodeItem(scrapy.Item):
    id = scrapy.Field()
    externalId = scrapy.Field()
    genres = scrapy.Field()
    description = scrapy.Field()
    title = scrapy.Field()
    rating = scrapy.Field()
    episodeNumber = scrapy.Field()
    seasonNumber = scrapy.Field()
    premiereDate = scrapy.Field()
    duration = scrapy.Field()


class HboItem(scrapy.Item):
    type = 'HboItem'
    id = scrapy.Field()
    title = scrapy.Field()
    externalId = scrapy.Field()
    description = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    goUrl = scrapy.Field()
    nowUrl = scrapy.Field()
    releaseDate = scrapy.Field()
    releaseYear = scrapy.Field()
    highDef = scrapy.Field()
    cast = scrapy.Field()
    crew = scrapy.Field()
    runtime = scrapy.Field()


class HboCastMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class HboCrewMember(scrapy.Item):
    name = scrapy.Field()
    role = scrapy.Field()
    order = scrapy.Field()


class ShowtimeItem(scrapy.Item):
    type = 'ShowtimeItem'
    id = scrapy.Field()
    title = scrapy.Field()
    externalId = scrapy.Field()
    description = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    url = scrapy.Field()
    seasons = scrapy.Field()
    releaseYear = scrapy.Field()
    cast = scrapy.Field()
    crew = scrapy.Field()


class ShowtimeItemSeason(scrapy.Item):
    seasonNumber = scrapy.Field()
    releaseDate = scrapy.Field()
    description = scrapy.Field()
    episodes = scrapy.Field()


class ShowtimeItemEpisode(scrapy.Item):
    episodeNumber = scrapy.Field()
    releaseDate = scrapy.Field()
    description = scrapy.Field()


class ShowtimeCastMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class ShowtimeCrewMember(scrapy.Item):
    name = scrapy.Field()
    role = scrapy.Field()
    order = scrapy.Field()


class AmazonItem(scrapy.Item):
    type = 'AmazonItem'
    id = scrapy.Field()
    title = scrapy.Field()
    externalId = scrapy.Field()
    description = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    url = scrapy.Field()
    releaseDate = scrapy.Field()
    releaseYear = scrapy.Field()
    cast = scrapy.Field()
    crew = scrapy.Field()
    runtime = scrapy.Field()
    availableOnPrime = scrapy.Field()
    offers = scrapy.Field()
    internalId = scrapy.Field()


class AmazonCastMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class AmazonCrewMember(scrapy.Item):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class AmazonItemOffer(scrapy.Item):
    offerType = scrapy.Field()
    price = scrapy.Field()
    currency = scrapy.Field()
    quality = scrapy.Field()
