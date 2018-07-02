import fs = require('fs');
import _ = require('lodash');
import { createConnection } from 'typeorm';
import { promisify } from 'util';

import GlobalConfig from '../src/Config';
import { ExternalSource, Network } from '../src/db/entity';
import { NetworkReference } from '../src/db/entity/NetworkReference';
import { NetworkRepository } from '../src/db/NetworkRepository';
import { slugify } from '../src/util/Slug';

async function main() {
    const connection = await createConnection(GlobalConfig.db);
    const repo = await connection.getCustomRepository(NetworkRepository);

    let networks = await promisify(fs.readFile)(process.cwd() + '/data/tv_network_ids_06_26_2018.txt');

    let p: any[][] = _.chain(networks.toString().split('\n')).
        filter(_.negate(_.isEmpty)).
        map(JSON.parse).
        // take(1).
        chunk(10). 
        value();

    for (let lines of p) {
        let slugs = lines.map(x => slugify(x.name));
        let networkBySlug = await repo.findNetworksBySlugs(new Set(slugs));

        let promises = lines.map(async (json: any) => {
            const slug = slugify(json.name);
            let ref = new NetworkReference();
            ref.externalSource = ExternalSource.TheMovieDb;
            ref.externalId = json.id.toString();

            let networkToSave: Network;

            if (!networkBySlug.has(slug)) {
                let network = new Network();
                network.name = json.name;
                network.slug = slug;
                network.shortname = slug;
                networkToSave = network;
            } else {
                networkToSave = networkBySlug.get(slug);
            }

            await connection.manager.save(Network, networkToSave);

            ref.network = networkToSave;
            await repo.saveNetworkReference(ref);
            
        });

        await Promise.all(promises);
    }
}

main();