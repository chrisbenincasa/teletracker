import { MovieDbClient } from 'themoviedb-client-typed';
import { MigrationInterface, QueryRunner } from 'typeorm';

import GlobalConfig from '../../Config';
import { slugify } from '../../util/Slug';
import { Genre, GenreProvider, GenreType } from '../entity/Genre';

export class SeedGenres1530024399000 implements MigrationInterface {
    movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);

    async up(queryRunner: QueryRunner) {
        let genres = await this.movieDbClient.genres.getMovieGenres();
        let genreModels = genres.map(genre => {
            let g = new Genre();
            g.name = genre.name;
            g.type = GenreType.Movie;
            g.externalIds = { [GenreProvider.themoviedb]: genre.id.toString() };
            g.slug = slugify(g.name);
            return g;
        });

        await queryRunner.manager.insert(Genre, genreModels);

        let tvGenres = await this.movieDbClient.genres.getTvGenres();
        let tvGenreModels = tvGenres.map(genre => {
            let g = new Genre();
            g.name = genre.name;
            g.type = GenreType.Tv;
            g.externalIds = { [GenreProvider.themoviedb]: genre.id.toString() };
            g.slug = slugify(g.name);
            return g;
        });

        await queryRunner.manager.insert(Genre, tvGenreModels);
    }

    async down(queryRunner: QueryRunner) {
        await queryRunner.query(`TRUNCATE TABLE "genres";`);
    }
}