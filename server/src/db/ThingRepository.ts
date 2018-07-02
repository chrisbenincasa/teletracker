import * as R from 'ramda';
import { EntityRepository, Repository } from 'typeorm';

import { Optional } from '../util/Types';
import * as Entity from './entity';
import { ExternalSource, ThingType, Network } from './entity';

@EntityRepository(Entity.Thing)
export class ThingRepository extends Repository<Entity.Thing> {
    // Assumes nested objects are entities and that they have an 'id' property
    // if both sides are an Array, merge them, take unique by 'id' field with right precendence
    // otherwise, just take whatever is passed in on the right side 
    private deepMergeWithConcat = R.mergeDeepWith<Entity.Thing, Entity.Thing>((l, r) => 
        R.ifElse(R.all(R.is(Array)), ([l, r]) => R.uniqBy(R.prop('id'))(r.concat(l)), ([_, r]) => r)([l, r])
    );

    async saveObject(thing: Entity.Thing): Promise<Entity.Thing> {
        let query = this.createQueryBuilder('thing').
            select().
            addSelect('metadata').
            where({normalizedName: thing.normalizedName}).
            getOne();

        return query.then(async foundThing => {
            let thingToSave = thing;
            
            if (foundThing) {
                thingToSave = this.deepMergeWithConcat(foundThing, thing);
            }

            return this.save(thingToSave);
        });
    }

    async getObjectById(showId: string | number): Promise<Optional<Entity.Thing>> {
        let query = this.manager.createQueryBuilder(Entity.Thing, 'thing').
            leftJoinAndSelect('thing.availability', 'availability', 'availability.isAvailable = :isAvailable', { isAvailable: true }).
            leftJoinAndSelect('availability.network', 'network').
            leftJoinAndSelect('thing.seasons', 'season').
            leftJoinAndSelect('thing.genres', 'genres').
            leftJoinAndSelect('season.episodes', 'episode').
            where({ id: showId });

        return query.getOne();
    }

    async getObjectsByIds(ids: Set<string | number>): Promise<Entity.Thing[]> {
        return this.findByIds(Array.from(ids))
    }

    async getObjectsByExternalIds(externalSource: ExternalSource, externalIds: Set<string>, type: ThingType): Promise<Entity.Thing[]> {
        if (externalIds.size == 0) {
            return Promise.resolve([]);
        }

        let query = this.createQueryBuilder('objects').
            where(`metadata->'${externalSource}'->'${type}'->>'id' IN (:...ids)`, { ids: Array.from(externalIds) });

        return query.getMany();
    }

    async getExternalIds(id: number): Promise<Optional<Entity.ThingExternalIds>> {
        return this.manager.findOne(Entity.ThingExternalIds, {
            where: {
                'thingId': id
            }
        });
    }

    async getShowById(showId: string | number, includeMetadata?: boolean): Promise<Optional<Entity.Thing>> {
        let query = this.createQueryBuilder('thing').
            select().
            where({ type: ThingType.Show, id: showId }).
            leftJoinAndSelect('thing.networks', 'networks').
            leftJoinAndSelect('thing.genres', 'genres');

        if (includeMetadata) {
            query = query.addSelect('metadata');
        }

        let seasonsAndEpisodes = this.manager.createQueryBuilder(Entity.TvShowSeason, 'season').
            where({ 'show': showId }).
            leftJoinAndSelect('season.episodes', 'episodes').
            leftJoinAndSelect('episodes.availability', 'availability').
            leftJoin('availability.network', 'network').
            addOrderBy('season.number', 'ASC').
            addOrderBy('episodes.number', 'ASC').
            addSelect('network.id');

        return Promise.all([query.getOne(), seasonsAndEpisodes.getMany()]).then(([thing, seasons]) => {
            if (thing) {
                thing.seasons = (seasons || []);
                return thing;
            } else {
                return null;
            }
        });
    }
}