import * as Router from 'koa-router';
import { Connection } from 'typeorm';

import { Controller } from './Controller';
import { MovieDbClient, SearchMoviesRequest } from 'themoviedb-client-typed';

export class TvShowController extends Controller {
    private dbConnection: Connection;
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.dbConnection = connection; // Hang onto a connection to the DB
        // This will be loaded via config soon.
        this.movieDbClient = new MovieDbClient(process.env.API_KEY);
    }

    setupRoutes(): void {
        this.router.get('/tv/search', async (ctx) => {
            let query = ctx.query.query;
            let ret = await this.movieDbClient.search.searchTvShows({ query: query });

            ctx.body = ret;
        });

        this.router.get('/tv/:id', async (ctx) => {
            let ret = await this.movieDbClient.tv.getTvShow(ctx.params.id as number);

            ctx.body = ret;
        });
    }
}