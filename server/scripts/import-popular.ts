import * as R from 'ramda';
import request = require('request-promise-native');
import { MovieDbClient } from 'themoviedb-client-typed';
import { createConnection } from 'typeorm';
import { promisify } from 'util';

import GlobalConfig from '../src/Config';
import { Availability, ExternalSource, Network, OfferType } from '../src/db/entity';
import { NetworkReference } from '../src/db/entity/NetworkReference';
import { NetworkRepository } from '../src/db/NetworkRepository';
import { ThingManager } from '../src/db/ThingManager';

const timer = async (ms: number) => {
    return promisify(setTimeout)(ms);
};

const looper = async function looper<T>(args: Iterator<[number, T]>, f: (x: T) => Promise<void>) {
    while (true) {
        let res = args.next();
        if (res.done) break;
        let [_, x] = res.value;
        await f(x);
        await timer(1000);
    }
}

interface JustWatchOffer {
    monetization_type: string,
    provider_id: number,
    retail_price: number,
    currency: string,
    presentation_type: string,
    date_created: string,
    country: string
}

function getOfferType(s: string): OfferType | undefined {
    switch(s.toLowerCase()) {
        case 'buy': return OfferType.Buy;
        case 'rent': return OfferType.Rent;
        case 'theater': return OfferType.Theater;
        case 'cinema': return OfferType.Theater;
        case 'subscription': return OfferType.Subscription;
        case 'flatrate': return OfferType.Subscription;
        case 'free': return OfferType.Free;
        case 'ads': return OfferType.Ads;
        default: 
            console.log('could not get offertype for type = ' + s);
            return undefined
    }
}

async function main() {
    let connection = await createConnection(GlobalConfig.db);

    let movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    let thingManager = new ThingManager(connection);
    let networkRepository = connection.getCustomRepository(NetworkRepository);

    let allNetworks = await networkRepository.find({ relations: ['references'] });
    let allNetworksByExternal = R.groupBy((n: Network) => R.find<NetworkReference>(R.propEq('externalSource', ExternalSource.JustWatch))(n.references).externalId)(allNetworks)

    for (let i = 0; i < 5; i++) {
        console.log(`Pulling page ${i} of TMDb's most popular movies`)
        let popular = await movieDbClient.movies.getPopular(null, i);

        console.log(`Got ${popular.results.length} popular movies from TMDb`);

        await looper(popular.results.entries(), async item => {
            let justWatchResult = await request('https://apis.justwatch.com/content/titles/en_US/popular', {
                qs: {
                    body: JSON.stringify({ page: 1, pageSize: 10, query: item.title, content_types: ['movie']})
                },
                json: true,
                gzip: true
            });
            
            let matchingJustWatch = R.find((jwItem: any) => {
                const idsMatch = R.find(R.allPass([R.propEq('provider_type', 'tmdb:id'), R.propEq('value', item.id)]))(jwItem.scoring);
                const namesMatch = R.propEq('title', item.title);
                const ogNamesMatch =  R.propEq('original_title', item.original_title);
                return idsMatch || namesMatch || ogNamesMatch;
            }, justWatchResult.items);
    
            const tmdbMovie = await movieDbClient.movies.getMovie(item.id, null, ['credits', 'release_dates']);
            let savedMovie = await thingManager.handleTMDbMovie(tmdbMovie);
    
            if (matchingJustWatch) {
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
    
                availabilityModels = await connection.getRepository(Availability).save(availabilityModels);
    
                savedMovie.availability = availabilityModels;
    
                await thingManager.thingRepository.save(savedMovie);
            }
        });
    }

    process.exit(0);
}

try {
    main();
} catch (e) {
    console.error(e);
}