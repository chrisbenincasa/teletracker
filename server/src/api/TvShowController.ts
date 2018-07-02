import * as Router from 'koa-router';
import { Connection } from 'typeorm';

import { ThingRepository } from '../db/ThingRepository';
import { Controller } from './Controller';

export class TvShowController extends Controller {
    private thingRepository: ThingRepository;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.thingRepository = connection.getCustomRepository(ThingRepository);
    }

    setupRoutes(): void {
        this.router.get('/shows/:id', async ctx => {
            return this.thingRepository.getShowById(ctx.params.id, true).then(show => {
                if (show) {
                    ctx.status = 200;
                    ctx.body = { data: show };
                } else {
                    ctx.status = 404;
                }
            });
        });

        this.router.get('/shows/:id/seasons', async ctx => {
            let includeMetadata = !!ctx.query.metadata;

        });
    }
}