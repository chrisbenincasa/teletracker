import 'mocha';

import * as chai from 'chai';
import { basename } from 'path';
import { Connection, QueryBuilder, Repository } from 'typeorm';
import * as uuid from 'uuid/v4';

import { Db } from '../db/Connection';
import { List, Thing, ThingType, User } from '../db/entity';
import TestBase from './base';
import { UserRepository } from '../db/UserRepository';

const should = chai.should();

class DbSpec extends TestBase {
    makeSpec(): Mocha.ISuite {
        let self = this;

        return describe('The DB', () => {
            let connection: Connection;
            let queryBuilder: QueryBuilder<User>;
            let userRepository: Repository<User>;

            before(async function () {
                this.timeout(self.timeout);
                await self.defaultBefore(true, false);
                
                connection = await new Db(self.config).connect('db.spec.ts').catch(e => { console.error(e); throw e });
            });

            after(async function () {
                this.timeout(self.timeout);
                await self.defaultAfter();
            });

            beforeEach(() => {
                userRepository = connection.getRepository(User);
                queryBuilder = userRepository.createQueryBuilder('user');
            });

            it('should insert and retrieve a user', async () => {
                let user = await generateUser();

                let foundUser = await queryBuilder.select().where('user.name = :name', { name: 'Gordon' }).getOne();
                chai.assert.exists(foundUser, 'foundUser exists');
            });

            it('should create a movie list for a user', async () => {
                let user = await generateUser();

                let show = new Thing();
                show.name = 'Halt and Catch Fire';
                show.normalizedName = 'halt-and-catch-fire';
                show.type = ThingType.Show;
                let showRet = await connection.getRepository(Thing).save(show);

                let showList = new List();
                showList.name = 'Test Show List';
                showList.user = Promise.resolve(user);
                showList.things = [showRet];
                showList = await connection.getRepository(List).save(showList);

                let userWithTrackedShows = await connection.getCustomRepository(UserRepository).getUserById(user.id, true);

                chai.assert.exists(userWithTrackedShows, 'user exists')
                chai.assert.lengthOf(userWithTrackedShows.lists, 2);
                chai.assert.lengthOf(userWithTrackedShows.lists[1].things, 1, 'user show list has 1 tracked show');

                chai.assert.ownInclude(userWithTrackedShows.lists[1].things[0], { name: show.name }, 'user tracked show has the correct props');
            });

            async function generateUser(): Promise<User> {
                let user = new User('Gordon');
                user.username = uuid();
                user.email = uuid();
                user.password = '12345';

                return connection.getCustomRepository(UserRepository).addUser(user);
            }
        });
    }
}

const scriptName = basename(__filename);

new DbSpec(scriptName).makeSpec();