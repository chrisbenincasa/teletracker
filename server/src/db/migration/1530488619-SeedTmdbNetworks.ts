import fs = require('fs');
import { MigrationInterface, QueryRunner } from 'typeorm';
import { promisify } from 'util';

export class SeedTmdbNetworks1530488619000 implements MigrationInterface {
    async up(queryRunner: QueryRunner) {
        let networks = await promisify(fs.readFile)(process.cwd() + '/data/tv_network_ids_06_26_2018.txt');
    }

    async down(queryRunner: QueryRunner) {

    }
}