import { EntityRepository, Repository } from 'typeorm';

import * as Entity from './entity';

@EntityRepository(Entity.Event)
export class EventsRepository extends Repository<Entity.Event> {
    async getEventsForUser(userId: string | number): Promise<Entity.Event[]> {
        return this.find({ where: { userId }, order: { timestamp: 'DESC' } });
    }

    async addEventForUser(user: Entity.User, event: Entity.Event): Promise<Entity.Event> {
        const ev = this.create(event);
        ev.user = user;
        return this.save(ev);
    }
}