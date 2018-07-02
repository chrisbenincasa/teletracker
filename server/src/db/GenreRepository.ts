import * as R from 'ramda';
import { EntityRepository, Repository, EntityManager } from 'typeorm';

import * as Entity from './entity';
import { ExternalSource, GenreType } from './entity';
import { Optional } from '../util/Types';

@EntityRepository(Entity.Genre)
export class GenreRepository extends Repository<Entity.Genre> {
    private genreReference: Repository<Entity.GenreReference>;

    constructor(manager: EntityManager) {
        super();
        this.genreReference = manager.getRepository(Entity.GenreReference);
    }

    async getGenreByExternalId(externalSource: ExternalSource, externalId: string): Promise<Optional<Entity.Genre>> {
        return this.genreReference.findOne({ where: { externalSource, externalId }, relations: ['genre'] }).then(R.prop('genre'));
    }

    async getGenreByExternalIds(externals?: [ExternalSource, string][], type?: GenreType) {
        let objs = (externals || []).map(([externalSource, externalId]) => { 
            return { externalSource, externalId };
        });

        let [genreCondition, genreParams] = type ? ['genre.type = :type', { type }] : [null, null];

        let queryBuilder = this.genreReference.createQueryBuilder('reference').
            innerJoinAndSelect('reference.genre', 'genre', genreCondition, genreParams);

        const whereClause = 'reference."externalSource" = :externalSource and reference."externalId" = :externalId'
        let query = objs.reduce((acc, o) => acc.orWhere(whereClause, o), queryBuilder);

        return query.getMany().then(refs => refs.map(R.prop('genre')));
    }
}