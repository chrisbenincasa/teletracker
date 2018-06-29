import * as R from 'ramda';
import request = require('request-promise-native');
import { MovieDbClient } from 'themoviedb-client-typed';
import { createConnection } from 'typeorm';

import GlobalConfig from '../src/Config';
import { Availability, ExternalSource, Network, OfferType } from '../src/db/entity';
import { NetworkReference } from '../src/db/entity/NetworkReference';
import { NetworkRepository } from '../src/db/NetworkRepository';
import { ThingManager } from '../src/db/ThingManager';
import { looper, getOfferType, JustWatchOffer } from './util';

async function main() {
    let connection = await createConnection(GlobalConfig.db);

    let movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    let thingManager = new ThingManager(connection);
    let networkRepository = connection.getCustomRepository(NetworkRepository);

    let allNetworks = await networkRepository.find({ relations: ['references'] });
    let allNetworksByExternal = R.groupBy((n: Network) => R.find<NetworkReference>(R.propEq('externalSource', ExternalSource.JustWatch))(n.references).externalId)(allNetworks)

    for (let i = 1; i <= 5; i++) {
        console.log(`Pulling page ${i} of TMDb's most popular movies`)
        let popular = await movieDbClient.tv.getPopular(null, i);

        console.log(`Got ${popular.results.length} popular TV from TMDb`);

        await looper(popular.results.entries(), async item => {
            let justWatchResult = await request('https://apis.justwatch.com/content/titles/en_US/popular', {
                qs: {
                    body: JSON.stringify({ page: 1, pageSize: 10, query: item.name, content_types: ['show'] })
                },
                json: true,
                gzip: true
            });

            let matchingJustWatch = R.find((jwItem: any) => {
                const idsMatch = R.find(R.allPass([R.propEq('provider_type', 'tmdb:id'), R.propEq('value', item.id)]))(jwItem.scoring);
                const namesMatch = R.propEq('title', item.name);
                const ogNamesMatch = R.propEq('original_title', item.name);
                return idsMatch || namesMatch || ogNamesMatch;
            }, justWatchResult.items);

            console.log('found match ', matchingJustWatch);

            // const tmdbMovie = await movieDbClient.movies.getMovie(item.id, null, ['credits', 'release_dates']);
            // let savedMovie = await thingManager.handleTMDbMovie(tmdbMovie);

            // if (matchingJustWatch) {
            //     let availabilityModels = R.reject(R.isNil, R.map((offer: JustWatchOffer) => {
            //         let provider = allNetworksByExternal[offer.provider_id.toString()]
            //         if (provider && provider[0]) {
            //             let av = new Availability();
            //             av.thing = savedMovie;
            //             av.network = provider[0];
            //             av.isAvailable = true;
            //             av.offerType = getOfferType(offer.monetization_type);
            //             av.region = offer.country; // Normalize
            //             av.startDate = new Date(offer.date_created);
            //             av.cost = offer.retail_price;
            //             av.currency = offer.currency;
            //             return av;
            //         } else {
            //             return;
            //         }
            //     })(matchingJustWatch.offers));

            //     availabilityModels = await connection.getRepository(Availability).save(availabilityModels);

            //     savedMovie.availability = availabilityModels;

            //     await thingManager.thingRepository.save(savedMovie);
            // }
        });
    }

    process.exit(0);
}

try {
    main();
} catch (e) {
    console.error(e);
}