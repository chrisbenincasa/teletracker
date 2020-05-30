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
    id = scrapy.Field()
    title = scrapy.Field()
    externalId = scrapy.Field()
    description = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    goUrl = scrapy.Field()
    nowUrl = scrapy.Field()
    releaseDate = scrapy.Field()
    highDef = scrapy.Field()


class ShowtimeItem(scrapy.Item):
    id = scrapy.Field()
    title = scrapy.Field()
    externalId = scrapy.Field()
    description = scrapy.Field()
    itemType = scrapy.Field()
    network = scrapy.Field()
    url = scrapy.Field()
    seasons = scrapy.Field()


class ShowtimeItemSeason(scrapy.Item):
    seasonNumber = scrapy.Field()
    releaseDate = scrapy.Field()
    description = scrapy.Field()
    episodes = scrapy.Field()


class ShowtimeItemEpisode(scrapy.Item):
    episodeNumber = scrapy.Field()
    releaseDate = scrapy.Field()
    description = scrapy.Field()