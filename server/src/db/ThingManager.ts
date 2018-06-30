import * as R from 'ramda';
import { Movie, TvShow } from 'themoviedb-client-typed';
import { Connection, Repository } from 'typeorm';

import { slugify } from '../util/Slug';
import { ExternalSource, Thing, ThingFactory } from './entity';
import { TvShowSeason } from './entity/TvShowSeason';
import { GenreRepository } from './GenreRepository';
import { NetworkRepository } from './NetworkRepository';
import { ThingRepository } from './ThingRepository';

export class ThingManager {
    private connection: Connection;
    thingRepository: ThingRepository;
    private tvSeasonRepository: Repository<TvShowSeason>;
    private networkRespoitory: NetworkRepository;
    private genreRepository: GenreRepository;
    // private tvEpisodeRespoitory: Repository<TvShowEpisode>;

    constructor(connection: Connection) {
        this.connection = connection;
        this.thingRepository = connection.getCustomRepository(ThingRepository);
        this.tvSeasonRepository = connection.getRepository(TvShowSeason);
        this.networkRespoitory = connection.getCustomRepository(NetworkRepository);
        this.genreRepository = connection.getCustomRepository(GenreRepository);
    }

    async handleTMDbMovie(movie: Movie): Promise<Thing> {
        let genreIds = movie.genre_ids || movie.genres.map(R.prop('id'));
        let genrePairs = genreIds.map(gid => [ExternalSource.TheMovieDb, gid.toString()] as [ExternalSource, string]);
        let genres = await this.genreRepository.getGenreByExternalIds(genrePairs);

        let thing = ThingFactory.movie(movie);
        thing.genres = genres;
        
        return this.thingRepository.saveObject(thing);
    }

    async handleTMDbTvShow(show: TvShow) {
        let networkSlugs = new Set(R.map(R.compose(slugify, R.prop('name')))(show.networks));
        let foundNetworks = await this.networkRespoitory.findNetworksBySlugs(networkSlugs);

        let thing = ThingFactory.show(show);
        thing.networks = R.map(([_, n]) => n, Array.from(foundNetworks.entries()));
        thing = await this.thingRepository.saveObject(thing);

        let seasonModels = show.seasons.map(season => {
            let seasonModel = new TvShowSeason();
            seasonModel.show = thing;
            seasonModel.number = season.season_number;
            return seasonModel;
        });

        await this.tvSeasonRepository.save(seasonModels);

        return thing;
    }
}