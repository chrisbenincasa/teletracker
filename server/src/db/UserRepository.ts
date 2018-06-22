import * as bcrypt from 'bcrypt';
import * as R from 'ramda';
import { EntityRepository, FindOneOptions, Repository } from 'typeorm';

import { Optional } from '../util/Types';
import * as Entity from './entity';

@EntityRepository(Entity.User)
export class UserRepository extends Repository<Entity.User> {
    async getAllUsers(): Promise<Entity.User[]> {
        return this.find();
    }

    private removePassword(user: Entity.User | undefined): Entity.User | undefined {
        if (!user) {
            return;
        } else {
            return R.omit(['password'], user);
        }
    }

    async getUserById(id: string | number, includeLists: boolean = false): Promise<Entity.User | null> {
        let baseRequest: FindOneOptions<Entity.User> = {
            where: { id: id }
        };

        if (includeLists) {
            baseRequest.join = {
                alias: 'user',
                leftJoinAndSelect: {
                    'lists': 'user.lists',
                    'shows': 'lists.things'
                }
            };
        }

        return this.findOne(baseRequest).then(this.removePassword);
    }

    async getUserByEmail(email: string): Promise<Entity.User | null> {
        return this.findOne({ where: { email: email } });
    }

    async addUser(user: Entity.User): Promise<Entity.User> {
        if (!user.password || user.password.length === 0) {
            return Promise.reject(new Error('Cannot create a user without a password'));
        }
        const salt = await bcrypt.genSalt();
        const hash = await bcrypt.hash(user.password, salt);
        user.password = hash;
        return this.save(user).then(this.removePassword).then(async user => {
            let list = new Entity.List;
            list.user = Promise.resolve(user);
            list.isDefault = true;
            list.name = 'Default List';

            
            list = await this.manager.save(Entity.List, list);

            user.lists = [list];

            return user;
        });
    }

    async getListForUser(userId: string | number, listId: string | number): Promise<Optional<Entity.List>> {
        // return this.getListForUser(userId, listId, 'showLists', 'shows');
        
        return this.manager.findOne(Entity.List, listId, {
            join: {
                alias: 'list',
                leftJoinAndSelect: {
                    shows: 'list.things',
                }
            }
        }).then(list => {
            if (list) {
                return list.user.then(user => {
                    return user && user.id == userId ? list : undefined;
                });
            } else {
                return;
            }
        });
    }
}