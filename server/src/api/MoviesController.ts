import * as Router from 'koa-router';
import { MovieDbClient } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import { ThingRepository } from '../db/ThingRepository';
import { Controller } from './Controller';
import { GenreRepository } from '../db/GenreRepository';
import { ExternalSource } from '../db/entity';

export class MoviesController extends Controller {
    private connection: Connection;
    private thingRepository: ThingRepository;
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.connection = connection;
        this.thingRepository = connection.getCustomRepository(ThingRepository);
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
            return this.thingRepository.getObjectById(ctx.params.id).then(show => {
                if (show) {
                    ctx.status = 200;
                    ctx.body = { data: show };
                } else {
                    console.error('show with id ' + ctx.params.id + ' not found');
                    ctx.status = 404;
                }
            });
        });

        this.router.get('/genres', async ctx => {
            return this.connection.getCustomRepository(GenreRepository).getGenreByExternalIds().then(x => {
                ctx.status = 200;
                ctx.body = { data: x }
            })
        })
    }
}