import { EntityRepository, Repository } from 'typeorm';

import * as Entity from './entity';

@EntityRepository(Entity.List)
export class ListRepository extends Repository<Entity.List> {
    async addObjectToList(show: Entity.Thing, list: Entity.List): Promise<Entity.List> {
        list.things = (list.things || []).concat([show]);
        return this.save(list);
    }
}
