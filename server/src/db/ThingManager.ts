import * as R from 'ramda';
import { Movie, TvShow, ExternalIds } from 'themoviedb-client-typed';
import * as TmdbModel from 'themoviedb-client-typed';
import { Connection, Repository } from 'typeorm';

import { slugify } from '../util/Slug';
import { ExternalSource, Thing, ThingFactory } from './entity';
import { TvShowSeason } from './entity/TvShowSeason';
import { GenreRepository } from './GenreRepository';
import { NetworkRepository } from './NetworkRepository';
import { ThingRepository } from './ThingRepository';
import { ThingExternalIds } from './entity/ThingExternalIds';
import { TvShowEpisode } from './entity/TvShowEpisode';
import { Entities } from 'html-entities';
import { TvSeasonRepository } from './TvSeasonRepository';

export class ThingManager {
    readonly thingRepository: ThingRepository;

    private connection: Connection;
    private tvSeasonRepository: TvSeasonRepository;
    private networkRespoitory: NetworkRepository;
    private genreRepository: GenreRepository;

    constructor(connection: Connection) {
        this.connection = connection;
        this.thingRepository = connection.getCustomRepository(ThingRepository);
        this.tvSeasonRepository = connection.getCustomRepository(TvSeasonRepository);
        this.networkRespoitory = connection.getCustomRepository(NetworkRepository);
        this.genreRepository = connection.getCustomRepository(GenreRepository);
    }

    async handleTMDbMovie(movie: Movie): Promise<Thing> {
        let genreIds = movie.genre_ids || movie.genres.map(R.prop('id')) || [];
        let genres = this.getTmdbGenres(genreIds)

        let thing = ThingFactory.movie(movie);

        await this.handleExternalIds(thing, movie.external_ids, movie.id.toString());
        
        thing.genres = await genres;
        
        return this.thingRepository.saveObject(thing);
    }

    async handleTMDbTvShow(show: TvShow, handleSeasons: boolean) {
        let genres = this.getTmdbGenres((show.genres || []).map(R.prop('id')));
        let networkSlugs = new Set(R.map(R.compose(slugify, R.prop('name')))(show.networks));
        let foundNetworks = await this.networkRespoitory.findNetworksBySlugs(networkSlugs);

        let thing = ThingFactory.show(show);
        thing.networks = R.map(([_, n]) => n, Array.from(foundNetworks.entries()));
        thing = await this.thingRepository.saveObject(thing);

        await this.handleExternalIds(thing, show.external_ids, show.id.toString());

        if (handleSeasons) {
            await this.tvSeasonRepository.getAllForShow(thing).then(seasons => {
                let promises = R.pipe(
                    R.propOr([], 'seasons'),
                    R.map<TmdbModel.TvShowSeason, Promise<TvShowSeason>>(async season => {
                        let existingSeason = ((await seasons) || []).find(R.propEq('number', season.season_number));
                        if (existingSeason) {
                            return Promise.resolve(existingSeason);
                        } else {
                            let seasonModel = this.tvSeasonRepository.create();
                            seasonModel.number = season.season_number;
                            seasonModel.show = thing;
                            return this.tvSeasonRepository.save(seasonModel);
                        }
                    })
                )(show.seasons);

                return Promise.all(promises);
            });
        }

        thing.genres = await genres;

        return thing;
    }

    private async handleExternalIds(entity: Thing | TvShowEpisode, externalIds?: ExternalIds, tmdbId?: string) {
        if (externalIds) {
            return this.connection.createQueryBuilder(ThingExternalIds, 'externalIds').
                where({ tmdbId: tmdbId || externalIds.id }).
                getOne().
                then(async existing => {
                    if (!existing) {
                        let model = new ThingExternalIds();

                        model.imdbId = externalIds.imdb_id;

                        if (tmdbId) {
                            model.tmdbId = tmdbId;
                        } else if (externalIds.id) {
                            model.tmdbId = externalIds.id.toString();
                        }

                        entity.externalIds = model;

                        return this.connection.createQueryBuilder().
                            insert().
                            into(ThingExternalIds).
                            values(model).
                            execute();
                    }
                });
        }
    }

    private async getTmdbGenres(genreIds: number[]) {
        let genrePairs = genreIds.map(gid => [ExternalSource.TheMovieDb, gid.toString()] as [ExternalSource, string]);
        return this.genreRepository.getGenreByExternalIds(genrePairs);
    }
}