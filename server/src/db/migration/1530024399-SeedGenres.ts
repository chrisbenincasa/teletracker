import * as R from 'ramda';
import { MovieDbClient } from 'themoviedb-client-typed';
import { MigrationInterface, QueryRunner } from 'typeorm';

import GlobalConfig from '../../Config';
import { slugify } from '../../util/Slug';
import { Genre, GenreProvider, GenreType } from '../entity/Genre';
import { GenreReference, ExternalSource } from '../entity';

export class SeedGenres1530024399000 implements MigrationInterface {
    movieDbClient = new MovieDbClient(GlobalConfig.themoviedb.apiKey);

    async up(queryRunner: QueryRunner) {
        let genres = await this.movieDbClient.genres.getMovieGenres();
        let genreModelsAndRefs = genres.map(genre => {
            let g = new Genre();
            g.name = genre.name;
            g.type = GenreType.Movie;
            g.externalIds = { [GenreProvider.themoviedb]: genre.id.toString() };
            g.slug = slugify(g.name);

            let ref = new GenreReference();
            ref.externalId = genre.id.toString();
            ref.externalSource = ExternalSource.TheMovieDb;
            ref.genre = g;

            g.references = [ref];

            return [g, ref] as [Genre, GenreReference];
        });

        let genreModels = R.map(([g, _]) => g, genreModelsAndRefs);
        let genreRefs = R.map(([_, g]) => g, genreModelsAndRefs);

        await queryRunner.manager.save(Genre, genreModels);
        await queryRunner.manager.save(GenreReference, genreRefs);

        let tvGenres = await this.movieDbClient.genres.getTvGenres();
        let tvGenreModelsAndRefs = tvGenres.map(genre => {
            let g = new Genre();
            g.name = genre.name;
            g.type = GenreType.Tv;
            g.externalIds = { [GenreProvider.themoviedb]: genre.id.toString() };
            g.slug = slugify(g.name);
            
            let ref = new GenreReference();
            ref.externalId = genre.id.toString();
            ref.externalSource = ExternalSource.TheMovieDb;
            ref.genre = g;

            g.references = [ref];

            return [g, ref] as [Genre, GenreReference];
        });

        let tvGenreModels = R.map(([g, _]) => g, tvGenreModelsAndRefs);
        let tvGenreRefs = R.map(([_, g]) => g, tvGenreModelsAndRefs);

        await queryRunner.manager.save(Genre, tvGenreModels);
        await queryRunner.manager.save(GenreReference, tvGenreRefs);
    }

    async down(queryRunner: QueryRunner) {
        let tableName = queryRunner.manager.getRepository(Genre).metadata.tableName;
        await queryRunner.query(`TRUNCATE TABLE "${tableName}" CASCADE;`);
    }
}