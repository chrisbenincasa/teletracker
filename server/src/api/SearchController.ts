import * as Router from 'koa-router';
import { Movie, MovieDbClient, MultiSearchResponse, Person, TvShow } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import * as Entity from '../db/entity';
import { ThingRepository } from '../db/ThingRepository';
import { Controller } from './Controller';

export class SearchController extends Controller {
    private thingRepository: ThingRepository;
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.thingRepository = connection.getCustomRepository(ThingRepository);
        // This will be loaded via config soon.
        this.movieDbClient = new MovieDbClient(process.env.API_KEY);
    }

    setupRoutes(): void {
        this.router.get('/search', async ctx => {
            const { query } = ctx.query;
            let response = await this.movieDbClient.search.searchMulti({ query });

            try {
                let enriched = await this.handleSearchMultiResult(response);
                ctx.status = 200;
                ctx.body = { data: enriched };
            } catch (e) {
                ctx.status = 500;
                ctx.body = { data: 'error' };
            }
        });
    }

    private async handleSearchMultiResult(result: MultiSearchResponse) {
        let savePromises = result.results.map(r => {
            let thing: Entity.Thing;
            if (r.media_type === 'movie') {
                thing = Entity.ThingFactory.movie(r as Movie);
            } else if (r.media_type === 'tv') {
                thing = Entity.ThingFactory.show(r as TvShow);
            } else {
                thing = Entity.ThingFactory.person(r as Person);
            }

            return this.thingRepository.saveObject(thing);
        });

        return Promise.all(savePromises);
    }
}