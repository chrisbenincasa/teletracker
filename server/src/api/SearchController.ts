import * as R from 'ramda';
import * as Router from 'koa-router';
import { Movie, MovieDbClient, MultiSearchResponse, Person, TvShow, MultiSearchResponseFields } from 'themoviedb-client-typed';
import { Connection } from 'typeorm';

import * as Entity from '../db/entity';
import { ThingRepository } from '../db/ThingRepository';
import { Controller } from './Controller';
import { ExternalSource, ThingType } from '../db/entity';
import { ThingManager } from '../db/ThingManager';

export class SearchController extends Controller {
    private thingManager: ThingManager;
    private thingRepository: ThingRepository;
    private movieDbClient: MovieDbClient;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.thingManager = new ThingManager(connection);
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
                console.error(e);
                ctx.status = 500;
                ctx.body = { data: 'error' };
            }
        });
    }

    // TODO: Clean this whole thing up
    private async handleSearchMultiResult(result: MultiSearchResponse) {
        let foundIds = new Set(result.results.map(i => i.id.toString()));

        // Find all of the objects we have already saved details for
        // TODO: Have to query for TV shows too. Probably easier to stick this in a join table
        // TODO: See if the object is "stale" and update if so
        let existingMovies: Entity.Thing[] = await this.thingRepository.getObjectsByExternalIds(ExternalSource.TheMovieDb, foundIds, ThingType.Movie);
        let existingTvShows: Entity.Thing[] = await this.thingRepository.getObjectsByExternalIds(ExternalSource.TheMovieDb, foundIds, ThingType.Show);

        // Create a mapping of "externalId" to the found objects
        let existingByExternalId: Map<string, Entity.Thing> = R.pipe(
            R.groupBy<Entity.Thing>(x => x.metadata.themoviedb.movie.id.toString()),
            R.mapObjIndexed(([thing]) => thing),
            Object.entries,
            (x: [string, Entity.Thing][]) => new Map<string, Entity.Thing>(x)
        )(existingMovies);

        let existingShowsByExternalId: Map<string, Entity.Thing> = R.pipe(
            R.groupBy<Entity.Thing>(x => x.metadata.themoviedb.show.id.toString()),
            R.mapObjIndexed(([thing]) => thing),
            Object.entries,
            x => new Map(x)
        )(existingTvShows);

        // Find objects from the search result that we're missing
        let missing = R.filter(i => !existingByExternalId.has(i.id.toString()) && !existingShowsByExternalId.has(i.id.toString()), result.results);

        // Take the top 5 saerch results and populate their rich data "synchronously"
        // TODO: Fire off Promise that saves the "async" ones
        let [missingSync, missingAsync] = R.splitAt(5)(missing);

        // For each item we want to fully populate before the route returns, query TMDb API,
        // save the object, and add it to a map (in parallel)
        let missingSavePromises = missingSync.map(m => {
            return this.processSingleSearchResult(m).then(p => [m.id, p] as [number, Entity.Thing]);
        });

        // Once all of our synchronous saves return, fold it all up
        // and return everything in its original order.
        return Promise.all(missingSavePromises).then(x => {
            // Don't start async procsesing until after the other processing is done
            missingAsync.forEach(this.processSingleSearchResult.bind(this));

            return R.reduce((acc, [key, value]) => acc.set(key, value), new Map<number, Entity.Thing>(), x);
        }).then(newlySaved => {
            let final = result.results.map(result => {
                if (result.media_type === 'movie') {
                    return existingByExternalId.get(result.id.toString()) || newlySaved.get(result.id);
                } else if (result.media_type === 'tv') {
                    return existingShowsByExternalId.get(result.id.toString()) || newlySaved.get(result.id);
                }
            });

            // This should be unnecessary once we're returning _all_ results.
            return R.reject(R.isNil, final);
        });
    }

    private async processSingleSearchResult(item: (Movie | TvShow | Person) & MultiSearchResponseFields) {
        let promise: Promise<Entity.Thing>;
        if (item.media_type === 'movie') {
            promise = this.movieDbClient.movies.getMovie(item.id, null, ['release_dates', 'credits', 'external_ids']).
                then(movie => this.thingManager.handleTMDbMovie(movie));
        } else if (item.media_type === 'tv') {
            promise = this.movieDbClient.tv.getTvShow(item.id, null, ['credits', 'external_ids']).
                then(tv => this.thingManager.handleTMDbTvShow(tv, true));
        } else {
            promise = this.movieDbClient.people.getPerson(item.id).
                then(p => this.thingRepository.save(Entity.ThingFactory.person(p)));
        }

        return promise;
    }
}