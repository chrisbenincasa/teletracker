import _ = require('lodash');
import * as R from 'ramda';
import request = require('request-promise-native');
import { MovieDbClient, TvShow } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import GlobalConfig from '../Config';
import { Availability, ExternalSource, Network, Thing, ThingExternalIds } from '../db/entity';
import { NetworkReference } from '../db/entity/NetworkReference';
import { TvShowEpisode } from '../db/entity/TvShowEpisode';
import { ThingManager } from '../db/ThingManager';
import { TvEpisodeRepository } from '../db/TvEpisodeRepository';
import { TvSeasonRepository } from '../db/TvSeasonRepository';
import { getOfferType, looper } from '../util';
import { Optional } from '../util/Types';

export class TvShowImporter {
    private makeMapKey = (externalSource: ExternalSource, externalId: string) => `${externalSource}_${externalId}`

    private connection: Connection;
    private tmdbClient: MovieDbClient;
    private thingManager: ThingManager;
    private seasonRepository: TvSeasonRepository;
    private episodeRepository: TvEpisodeRepository;

    constructor(connection: Connection) {
        this.connection = connection;
        this.tmdbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
        this.thingManager = new ThingManager(connection);
        this.seasonRepository = connection.getCustomRepository(TvSeasonRepository);
        this.episodeRepository = connection.getCustomRepository(TvEpisodeRepository);
    }

    async handleTmdbShow(
        tmdbShow: TvShow, 
        saveSeasons: boolean, 
        saveAvailability: boolean,
        networksByKeyFunc: () => { [index: string]: [Network, NetworkReference][] }
    ) {
        let networksByKey = networksByKeyFunc();
        let savedShow = await this.thingManager.handleTMDbTvShow(tmdbShow, false);

        if (saveSeasons) {
            try {
                let savedSeasons = await this.saveSeasons(tmdbShow, savedShow);

                savedShow.seasons = savedSeasons;
                savedShow = await this.thingManager.thingRepository.saveObject(savedShow);
            } catch (e) {
                console.error('error saving seasons', e);
            }
        }

        if (saveAvailability) {
            try {
                let matchingJustWatch = await this.findJustWatchShow(tmdbShow);
    
                await this.handleJustWatchItem(matchingJustWatch, savedShow, () => networksByKey);
            } catch (e) {
                console.error('error saving availability', e);
            }
        }
    }

    async saveSeasons(show: TvShow, showModel: Thing) {
        if (R.isNil(showModel.id)) {
            return Promise.reject(new Error('Cannot save seasons on a show that hasn\'t been saved yet'));
        }

        let externalIdsRepo = this.connection.getRepository(ThingExternalIds);

        let existingSeasons = this.seasonRepository.getAllForShow(showModel);

        let seasons = (show.seasons || []);

        return looper(seasons.entries(), async season => {
            let foundSeason = await this.tmdbClient.tv.getTvShowSeason(
                show.id,
                season.season_number,
                null,
                ['images', 'videos']
            );

            let savedEpisodes = await Promise.all(
                foundSeason.episodes.map(async episode => {
                    return this.episodeRepository.findByExternalId(ExternalSource.TheMovieDb, episode.id.toString()).then(async existingEpisode => {
                        if (existingEpisode) {
                            existingEpisode.name = episode.name;
                            existingEpisode.productionCode = episode.production_code;
                            existingEpisode.number = episode.episode_number;
                            return this.episodeRepository.
                                update(existingEpisode.id, R.omit(['id', 'number', 'lastUpdatedAt'], existingEpisode)).
                                then(() => existingEpisode);
                        } else {
                            console.log('could not find episode with external id = ' + episode.id.toString());
                            let epModel = this.connection.manager.create(TvShowEpisode);
                            epModel.name = episode.name;
                            epModel.productionCode = episode.production_code;
                            epModel.number = episode.episode_number;
                            return this.episodeRepository.save(epModel);
                        }
                    });
                })
            );

            let x = savedEpisodes.map(async episode => {
                let external = externalIdsRepo.create();
                external.tmdbId = episode.id.toString();
                external.tvEpisode = episode;
                let q = externalIdsRepo.
                    createQueryBuilder().
                    insert().
                    into(ThingExternalIds).
                    values({ tmdbId: episode.id.toString(), tvEpisode: episode }).
                    onConflict('DO NOTHING');

                await q.execute().
                    then(res => {
                        if (res.identifiers && res.identifiers.length > 0 && res.identifiers[0]) {
                            external.id = res.identifiers[0].id
                        }
                    });

                episode.externalIds = external;
                // await this.connection.manager.update(TvShowEpisode, episode.id, episode);
            });

            await Promise.all(x);

            let existingSeason = ((await existingSeasons) || []).find(R.propEq('number', season.season_number));

            if (existingSeason) {
                return existingSeason;
            } else {
                let seasonModel = this.seasonRepository.create();
                seasonModel.number = season.season_number;
                seasonModel.show = showModel;
                seasonModel.episodes = savedEpisodes;
                return this.seasonRepository.save(seasonModel);
            }
        });
    }

    async findJustWatchShow(show: Partial<TvShow>): Promise<Optional<any>> {
        let justWatchResult = await request('https://apis.justwatch.com/content/titles/en_US/popular', {
            qs: {
                body: JSON.stringify({
                    page: 1,
                    pageSize: 10,
                    query: show.name,
                    content_types: ['show']
                })
            },
            json: true,
            gzip: true
        });

        if (justWatchResult && justWatchResult.items) {
            return R.find((jwItem: any) => {
                const idsMatch = jwItem.scoring ? R.find(R.allPass([R.propEq('provider_type', 'tmdb:id'), R.propEq('value', show.id)]))(jwItem.scoring) : false;
                const namesMatch = R.propEq('title', show.name);
                const yearMatch = R.propEq('original_release_year', show.first_air_date.split('-')[0]);
                const ogNamesMatch = R.propEq('original_title', show.name);
                return idsMatch || (namesMatch && yearMatch) || (ogNamesMatch && yearMatch);
            }, justWatchResult.items);
        }
    }

    async handleJustWatchItem(
        item: Optional<any>,
        showModel: Thing,
        networksByKeyFunc: () => { [index: string]: [Network, NetworkReference][] }
    ) {
        let networksByKey = networksByKeyFunc();

        if (item) {
            let justWatchDetails = await request(`https://apis.justwatch.com/content/titles/show/${item.id}/locale/en_US`, {
                json: true,
                gzip: true
            });

            justWatchDetails.seasons.forEach(async (season: any) => {
                let justWatchSeason = await request(`https://apis.justwatch.com/content/titles/show_season/${season.id}/locale/en_US`, {
                    json: true,
                    gzip: true
                });

                let allAvailabilities = justWatchSeason.episodes.map((episode: any) => {
                    let ep = Thing.getEpisode(showModel, season.season_number, episode.episode_number);
                    let avs = (episode.offers || []).map((offer: any) => {
                        let [provider, _] = networksByKey[this.makeMapKey(ExternalSource.JustWatch, offer.provider_id.toString())]
                        if (provider && provider[0] && ep) {
                            let av = new Availability();
                            av.tvShowEpisode = ep;
                            av.network = provider[0];
                            av.isAvailable = true;
                            av.offerType = getOfferType(offer.monetization_type);
                            av.region = offer.country; // Normalize
                            av.startDate = new Date(offer.date_created);
                            av.cost = offer.retail_price;
                            av.currency = offer.currency;
                            return av;
                        } else {
                            return null;
                        }
                    }) as Optional<Availability>[];

                    return R.reject<Availability>(R.isNil)(avs);
                }) as Availability[][];

                let p = _.filter(_.flatten(allAvailabilities), _.negate(_.isNil))
                if (p.length > 0) {
                    await this.connection.manager.insert(Availability, p);
                }
            });
        }
    }
}