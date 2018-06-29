import * as R from 'ramda';
import request = require('request-promise-native');
import { Movie, MovieDbClient } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import { getOfferType, JustWatchOffer, looper } from '.';
import GlobalConfig from '../../src/Config';
import { Availability, ExternalSource, Network } from '../../src/db/entity';
import { NetworkReference } from '../../src/db/entity/NetworkReference';
import { NetworkRepository } from '../../src/db/NetworkRepository';
import { ThingManager } from '../../src/db/ThingManager';

export class MovieImporter {
    readonly movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    readonly connection: Connection;
    readonly thingManager: ThingManager;
    readonly networkRepository: NetworkRepository;

    constructor(connection: Connection) {
        this.connection = connection;
        this.thingManager = new ThingManager(connection);
        this.networkRepository = connection.getCustomRepository(NetworkRepository);
    }

    async handleMovies(movies: IterableIterator<[number, Partial<Movie>]>) {
        let allNetworks = await this.networkRepository.find({ relations: ['references'] });
        let allNetworksByExternal = R.groupBy((n: Network) => 
            R.find<NetworkReference>(R.propEq('externalSource', ExternalSource.JustWatch)
        )(n.references).externalId)(allNetworks)

        await looper(movies, async item => {
            let justWatchResult = await request('https://apis.justwatch.com/content/titles/en_US/popular', {
                qs: {
                    body: JSON.stringify({ page: 1, pageSize: 10, query: item.title, content_types: ['movie'] })
                },
                json: true,
                gzip: true
            });

            let matchingJustWatch = R.find((jwItem: any) => {
                const idsMatch = R.find(R.allPass([R.propEq('provider_type', 'tmdb:id'), R.propEq('value', item.id)]))(jwItem.scoring);
                const namesMatch = R.propEq('title', item.title);
                const ogNamesMatch = R.propEq('original_title', item.original_title);
                return idsMatch || namesMatch || ogNamesMatch;
            }, justWatchResult.items);

            const tmdbMovie = await this.movieDbClient.movies.getMovie(item.id, null, ['credits', 'release_dates']);
            let savedMovie = await this.thingManager.handleTMDbMovie(tmdbMovie);

            if (matchingJustWatch && matchingJustWatch.offers) {
                let availabilityModels = R.reject(R.isNil, R.map((offer: JustWatchOffer) => {
                    let provider = allNetworksByExternal[offer.provider_id.toString()]
                    if (provider && provider[0]) {
                        let av = new Availability();
                        av.thing = savedMovie;
                        av.network = provider[0];
                        av.isAvailable = true;
                        av.offerType = getOfferType(offer.monetization_type);
                        av.region = offer.country; // Normalize
                        av.startDate = new Date(offer.date_created);
                        av.cost = offer.retail_price;
                        av.currency = offer.currency;
                        return av;
                    } else {
                        return;
                    }
                })(matchingJustWatch.offers));

                availabilityModels = await this.connection.getRepository(Availability).save(availabilityModels);

                savedMovie.availability = availabilityModels;

                await this.thingManager.thingRepository.save(savedMovie);
            } else {
                console.log(`no offers found for movie ${item.title} with justwatch id = ${matchingJustWatch}`)
            }
        });
    }
}