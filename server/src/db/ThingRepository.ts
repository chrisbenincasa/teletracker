import * as R from 'ramda';
import { EntityRepository, Repository } from 'typeorm';

import { Optional } from '../util/Types';
import * as Entity from './entity';
import { ExternalSource, ThingType } from './entity';

@EntityRepository(Entity.Thing)
export class ThingRepository extends Repository<Entity.Thing> {
    async saveObject(movie: Entity.Thing) {
        return this.findOne({ where: { normalizedName: movie.normalizedName } }).then(async foundMovie => {
            if (foundMovie) {
                let newMovie = R.mergeDeepRight(foundMovie, movie)
                await this.update(foundMovie.id, newMovie);
                return newMovie;
            } else {
                return this.save(movie);
            }
        });
    }

    async getObjectById(showId: string | number): Promise<Optional<Entity.Thing>> {
        let query = this.manager.createQueryBuilder(Entity.Thing, 'thing').
            leftJoinAndSelect('thing.availability', 'availability', 'availability.isAvailable = :isAvailable', { isAvailable: true }).
            leftJoinAndSelect('availability.network', 'network').
            leftJoinAndSelect('thing.seasons', 'season').
            leftJoinAndSelect('season.episodes', 'episode').
            where({ id: showId });

        return query.getOne();
    }

    async getObjectsByIds(ids: Set<string | number>): Promise<Entity.Thing[]> {
        return this.findByIds(Array.from(ids))
    }

    async getObjectsByExternalIds(externalSource: ExternalSource, externalIds: Set<string>, type: ThingType): Promise<any> {
        let query = this.createQueryBuilder('objects').
            where(`metadata->'${externalSource}'->'${type}'->>'id' IN (:...ids)`, { ids: Array.from(externalIds) });

        return query.getMany()
    }

    async getShowById(showId: string | number): Promise<Optional<Entity.Thing>> {
        let query = this.manager.createQueryBuilder(Entity.Thing, 'thing').
            leftJoinAndSelect('thing.seasons', 'season').
            leftJoinAndSelect('season.episodes', 'episode').
            leftJoinAndSelect('episode.availability', 'availability', 'availability.isAvailable = :isAvailable', { isAvailable: true }).
            leftJoinAndSelect('availability.network', 'network').
            where({ id: showId, type: Entity.ThingType.Show }).
            select(['thing', 'season', 'episode', 'availability', 'network.name']);

        return query.getOne();
    }
}