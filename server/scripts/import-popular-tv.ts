import * as R from 'ramda';
import { MovieDbClient } from 'themoviedb-client-typed';
import { createConnection } from 'typeorm';

import GlobalConfig from '../src/Config';
import { ExternalSource, Network } from '../src/db/entity';
import { NetworkReference } from '../src/db/entity/NetworkReference';
import { NetworkRepository } from '../src/db/NetworkRepository';
import { looper } from '../src/util';
import { TvShowImporter } from '../src/util/ShowImporter';

const makeMapKey = (externalSource: ExternalSource, externalId: string) => `${externalSource}_${externalId}`

async function main() {
    let connection = await createConnection(GlobalConfig.db);

    let movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);
    let networkRepository = connection.getCustomRepository(NetworkRepository);
    let showImporter = new TvShowImporter(connection);

    let allNetworks = await networkRepository.find({ relations: ['references'] });

    let networksByKey = R.groupBy(
        ([_, nr]) => makeMapKey(nr.externalSource, nr.externalId),
        R.chain(n => n.references.map(r => [n, r] as [Network, NetworkReference]), allNetworks)
    )

    for (let i = 1; i <= 5; i++) {
        // let popular = await movieDbClient.tv.getPopular(null, i);
        let popular = await movieDbClient.tv.getTopRated(null, i);

        await looper(popular.results.entries(), async item => {
            const tmdbShow = await movieDbClient.tv.getTvShow(item.id, null, ['credits', 'release_dates', 'external_ids']);

            await showImporter.handleTmdbShow(tmdbShow, true, true, () => networksByKey);
        });
    }

    process.exit(0);
}

try {
    main();
} catch (e) {
    console.error(e);
}