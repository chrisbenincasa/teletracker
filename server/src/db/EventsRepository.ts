import { EntityRepository, Repository } from 'typeorm';

import * as Entity from './entity';
import R = require('ramda');
import { EventType, TargetEntityType } from './entity';
import _ = require('lodash');

@EntityRepository(Entity.Event)
export class EventsRepository extends Repository<Entity.Event> {
    async getEventsForUser(userId: string | number): Promise<Entity.Event[]> {
        return this.find({ where: { userId }, order: { timestamp: 'DESC' } }).then(events => {
            let eventsByType = R.groupBy<Entity.Event>(ev => ev.targetEntityType)(events);

            let joined = R.map(async ([type, events]) => {
                let ids = R.map(R.prop('targetEntityId'), events);
                if (type == TargetEntityType.Show) {
                    return this.manager.findByIds(Entity.Thing, ids).then(foundThings => {
                        let thingsById = _.groupBy(foundThings, _.property('id'));
                        return events.map(event => {
                            if (thingsById[event.targetEntityId] && thingsById[event.targetEntityId].length > 0) {
                                event.targetEntity = thingsById[event.targetEntityId][0];
                            }
                            return event;

                        }).filter((ev) => !R.isNil(ev.targetEntity));
                    });
                } else {
                    return;
                }
            }, Object.entries(eventsByType))

            
            return Promise.all(joined).then(_.flatten).then(events => {
                return events.sort((ev, ev2) => ev2.timestamp.getTime() - ev.timestamp.getTime());
            });
        });
    }

    async addEventForUser(user: Entity.User, event: Entity.Event): Promise<Entity.Event> {
        const ev = this.create(event);
        ev.user = user;
        return this.save(ev);
    }
}