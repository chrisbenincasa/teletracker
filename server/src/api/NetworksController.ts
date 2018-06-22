import * as Router from 'koa-router';
import { Connection } from 'typeorm';

import { NetworkRepository } from '../db/NetworkRepository';
import { Controller } from './Controller';

export class NetworksController extends Controller {
    private networksRepository: NetworkRepository;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.networksRepository = connection.getCustomRepository(NetworkRepository);
    }

    setupRoutes(): void {
        this.router.get('/networks', async ctx => {
            let loadAvailability = !!ctx.query.availability;
            return this.networksRepository.getNetworks(null, loadAvailability).then(networks => {
                ctx.status = 200;
                ctx.body = { data: networks };
            });
        });

        this.router.get('/networks/:id/availability', async ctx => {
            // return this.dbAccess
        });
    }
}