import fs = require('fs');
import { MigrationInterface, QueryRunner } from 'typeorm';
import { promisify } from 'util';
import { Network, ExternalSource } from '../entity';
import { slugify } from '../../util/Slug';
import { NetworkReference } from '../entity/NetworkReference';

export class SeedNetworks1530023751000 implements MigrationInterface {
    async up(queryRunner: QueryRunner) {
        // let networks = await promisify(fs.readFile)(process.cwd() + '/data/tv_network_ids_06_26_2018.txt');
        let providers = await promisify(fs.readFile)(process.cwd() + '/data/providers.json');

        JSON.parse(providers.toString()).forEach(async (provider: any) => {
            let network = new Network();
            network.name = provider.clear_name;
            network.slug = slugify(provider.clear_name);
            network.shortname = provider.short_name;

            let { identifiers: [{id}]} = await queryRunner.manager.insert(Network, network);
            network.id = id;

            let reference = new NetworkReference();
            reference.network = network;
            reference.externalSource = ExternalSource.JustWatch;
            reference.externalId = provider.id.toString();

            await queryRunner.manager.insert(NetworkReference, reference)

            network.references = (network.references || []).concat([reference]);

            await queryRunner.manager.save(Network, network);
            
            console.log('done')
        });
    }

    async down(queryRunner: QueryRunner) {
        await queryRunner.query('TRUNCATE TABLE networks CASCADE;');
        await queryRunner.query('TRUNCATE TABLE network_references CASCADE;');
    }
}