import * as R from 'ramda';
import { Movie, TvShow, ExternalIds } from 'themoviedb-client-typed';
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

export class ThingManager {
    readonly thingRepository: ThingRepository;

    private connection: Connection;
    private tvSeasonRepository: Repository<TvShowSeason>;
    private networkRespoitory: NetworkRepository;
    private genreRepository: GenreRepository;

    constructor(connection: Connection) {
        this.connection = connection;
        this.thingRepository = connection.getCustomRepository(ThingRepository);
        this.tvSeasonRepository = connection.getRepository(TvShowSeason);
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
            let seasonModels = show.seasons.map(season => {
                let seasonModel = new TvShowSeason();
                seasonModel.show = thing;
                seasonModel.number = season.season_number;
                return seasonModel;
            });

            await this.tvSeasonRepository.save(seasonModels);
        }

        thing.genres = await genres;

        return thing;
    }

    private async handleExternalIds(entity: Thing | TvShowEpisode, externalIds?: ExternalIds, tmdbId?: string) {
        if (externalIds) {
            let model = new ThingExternalIds();

            model.imdbId = externalIds.imdb_id;

            if (externalIds.id) {
                model.tmdbId = externalIds.id.toString();
            } else if (tmdbId) {
                model.tmdbId = tmdbId;
            }

            entity.externalIds = model;

            let baseQuery = this.connection.createQueryBuilder().
                insert().
                into(ThingExternalIds).
                values(model);

            if (<Thing>entity) {
                model.thing = (<Thing>entity);
                baseQuery = baseQuery.onConflict(`("thingId") DO UPDATE SET "imdbId" = :imdbId, "lastUpdatedAt" = :lastUpdatedAt`);
            } else {
                model.tvEpisode = (<TvShowEpisode>entity);
                baseQuery = baseQuery.onConflict(`("tvEpisodeId") DO UPDATE SET "imdbId" = :imdbId, "lastUpdatedAt" = :lastUpdatedAt`);
            }
            
            return baseQuery.
                setParameters({imdbId: model.imdbId, lastUpdatedAt: new Date()}).
                execute();
        }
    }

    private async getTmdbGenres(genreIds: number[]) {
        let genrePairs = genreIds.map(gid => [ExternalSource.TheMovieDb, gid.toString()] as [ExternalSource, string]);
        return this.genreRepository.getGenreByExternalIds(genrePairs);
    }
}