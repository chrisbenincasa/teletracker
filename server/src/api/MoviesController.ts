import * as Router from 'koa-router';
import { MovieDbClient } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import { Controller } from './Controller';

export class MoviesController extends Controller {
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        // This will be loaded via config soon.
        this.movieDbClient = new MovieDbClient(process.env.API_KEY);
    }

    setupRoutes(): void {
        this.router.get('/movies/search', async (ctx) => {
            let query = ctx.query.query;
            let ret = await this.movieDbClient.search.searchMovies({ query: query });

            ctx.body = ret;
        });

        this.router.get('/movies/:id', async (ctx) => {
            let ret = await this.movieDbClient.movies.getMovie(ctx.params.id as number);

            ctx.body = ret;
        });
    }
}