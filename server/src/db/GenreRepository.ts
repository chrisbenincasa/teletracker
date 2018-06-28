import * as R from 'ramda';
import { EntityRepository, Repository, EntityManager } from 'typeorm';

import * as Entity from './entity';
import { ExternalSource } from './entity';
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

    async getGenreByExternalIds(externals?: [ExternalSource, string][]) {
        let objs = (externals || []).map(([externalSource, externalId]) => { 
            return { externalSource, externalId };
        });

        let queryBuilder = this.genreReference.createQueryBuilder('reference').leftJoinAndSelect('reference.genre', 'genre');
        let query = objs.reduce((acc, o) => acc.orWhere('reference."externalSource" = :externalSource and reference."externalId" = :externalId', o), queryBuilder);

        return query.getMany().then(refs => refs.map(R.prop('genre')));
    }
}