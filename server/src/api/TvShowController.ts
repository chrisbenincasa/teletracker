import * as Router from 'koa-router';
import { MovieDbClient } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import { ThingRepository } from '../db/ThingRepository';
import { Controller } from './Controller';

export class TvShowController extends Controller {
    private thingRepository: ThingRepository;
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.thingRepository = connection.getCustomRepository(ThingRepository);
        // This will be loaded via config soon.
        this.movieDbClient = new MovieDbClient(process.env.API_KEY);
    }

    setupRoutes(): void {
        this.router.get('/tv/search', async (ctx) => {
            let query = ctx.query.query;
            let ret = await this.movieDbClient.search.searchTvShows({ query });

            // TODO: Augment the response with saved data from our DB

            ctx.body = ret;
        });

        this.router.get('/tv/:id', async (ctx) => {
            let ret = await this.movieDbClient.tv.getTvShow(ctx.params.id as number);

            ctx.body = ret;
        });

        this.router.get('/shows/:id', async ctx => {
            return this.thingRepository.getShowById(ctx.params.id).then(show => {
                if (show) {
                    ctx.status = 200;
                    ctx.body = { data: show };
                } else {
                    ctx.status = 404;
                }
            });
        });
    }
}