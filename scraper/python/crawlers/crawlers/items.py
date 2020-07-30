# -*- coding: utf-8 -*-

# Define here the models for your scraped items
#
# See documentation in:
# https://docs.scrapy.org/en/latest/topics/items.html

import scrapy


class BaseItem(scrapy.Item):
    version = scrapy.Field()


class NetflixItem(BaseItem):
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


class NetflixCastMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class NetflixCrewMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class NetflixItemSeason(BaseItem):
    seasonNameRaw = scrapy.Field()
    seasonNumber = scrapy.Field()
    releaseYear = scrapy.Field()
    description = scrapy.Field()
    episodes = scrapy.Field()


class NetflixItemEpisode(BaseItem):
    seasonNumber = scrapy.Field()
    episodeNumber = scrapy.Field()
    name = scrapy.Field()
    runtime = scrapy.Field()
    description = scrapy.Field()


class HuluItem(BaseItem):
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


class HuluEpisodeItem(BaseItem):
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


class HboItem(BaseItem):
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


class HboCastMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class HboCrewMember(BaseItem):
    name = scrapy.Field()
    role = scrapy.Field()
    order = scrapy.Field()


class ShowtimeItem(BaseItem):
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


class ShowtimeItemSeason(BaseItem):
    seasonNumber = scrapy.Field()
    releaseDate = scrapy.Field()
    description = scrapy.Field()
    episodes = scrapy.Field()


class ShowtimeItemEpisode(BaseItem):
    episodeNumber = scrapy.Field()
    releaseDate = scrapy.Field()
    description = scrapy.Field()


class ShowtimeCastMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class ShowtimeCrewMember(BaseItem):
    name = scrapy.Field()
    role = scrapy.Field()
    order = scrapy.Field()


class AmazonItem(BaseItem):
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


class AmazonCastMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class AmazonCrewMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class AmazonItemOffer(BaseItem):
    offerType = scrapy.Field()
    price = scrapy.Field()
    currency = scrapy.Field()
    quality = scrapy.Field()


class AppleTvItem(BaseItem):
    type = 'AppleTv'
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
    offers = scrapy.Field()


class AppleTvItemOffer(BaseItem):
    offerType = scrapy.Field()
    price = scrapy.Field()
    currency = scrapy.Field()
    quality = scrapy.Field()


class AppleTvCastMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()


class AppleTvCrewMember(BaseItem):
    name = scrapy.Field()
    order = scrapy.Field()
    role = scrapy.Field()
