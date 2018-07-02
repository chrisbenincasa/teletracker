import * as R from 'ramda';
import { MovieDbClient, PagedResult, TvShow } from 'themoviedb-client-typed';
import { createConnection } from 'typeorm';

import GlobalConfig from '../src/Config';
import { ExternalSource, Network } from '../src/db/entity';
import { NetworkReference } from '../src/db/entity/NetworkReference';
import { NetworkRepository } from '../src/db/NetworkRepository';
import { looper } from '../src/util';
import { TvShowImporter } from '../src/util/ShowImporter';

const makeMapKey = (externalSource: ExternalSource, externalId: string) => `${externalSource}_${externalId}`

async function main(args: string[]) {
    let connection = await createConnection(GlobalConfig.db);

    let movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    let networkRepository = connection.getCustomRepository(NetworkRepository);
    let showImporter = new TvShowImporter(connection);

    let allNetworks = await networkRepository.find({ relations: ['references'] });

    let networksByKey = R.groupBy(
        ([_, nr]) => makeMapKey(nr.externalSource, nr.externalId),
        R.chain(n => n.references.map(r => [n, r] as [Network, NetworkReference]), allNetworks)
    )

    let provider: (page: number) => Promise<PagedResult<Partial<TvShow>>>;

    let pages = args.length > 1 ? args[1] : 5;

    if (args[0] === 'popular') {
        provider = (page: number) => movieDbClient.tv.getPopular(null, page);
    } else if (args[0] === 'airing_today') {
        provider = (page: number) => movieDbClient.tv.getAiringToday(null, page);
    } else if (args[0] === 'top_rated') {
        provider = (page: number) => movieDbClient.tv.getTopRated(null, page);
    } else if (args[0] === 'on_the_air') {
        provider = (page: number) => movieDbClient.tv.getOnTheAir(null, page);
    } else {
        console.log('TV type ' + args[0] + ' not supported');
        return;
    }

    for (let i = 1; i <= pages; i++) {
        // let popular = await movieDbClient.tv.getPopular(null, i);
        let popular = await provider(i);

        await looper(popular.results.entries(), async item => {
            const tmdbShow = await movieDbClient.tv.getTvShow(item.id, null, ['credits', 'release_dates', 'external_ids']);

            await showImporter.handleTmdbShow(tmdbShow, true, true, () => networksByKey);
        });
    }

    process.exit(0);
}

const args = process.argv.slice(2);

try {
    main(args);
} catch (e) {
    console.error(e);
}