import * as Router from 'koa-router';
import { MovieDbClient, PagedResult, Movie, TvShow, Person } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import DbAccess from '../db/DbAccess';
import { Controller } from './Controller';
import * as Entity from '../db/entity';

export class SearchController extends Controller {
    private dbAccess: DbAccess;
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.dbAccess = new DbAccess(connection);
        // This will be loaded via config soon.
        this.movieDbClient = new MovieDbClient(process.env.API_KEY);
    }

    setupRoutes(): void {
        this.router.get('/search', async ctx => {
            const { query } = ctx.query;
            let response = await this.movieDbClient.search.searchMulti({ query });

            this.handleSearchMultiResult(response);

            ctx.body = response;
        });
    }

    private async handleSearchMultiResult(result: PagedResult<Movie | TvShow | Person>) {
        result.results.forEach(r => {
            if ((<Movie>r).title) {
                const entity = this.movieToEntity(r as Movie);
                this.dbAccess.saveMovie(entity)
            }
        })
    }

    private movieToEntity(movie: Movie): Entity.Movie {
        let entity = new Entity.Movie;
        entity.externalId = movie.id.toString();
        entity.name = movie.title;
        return entity;
    }
}