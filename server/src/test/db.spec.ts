import 'mocha';

import * as chai from 'chai';
import * as random from 'random-js';
import { Connection, QueryBuilder, Repository } from 'typeorm';

import { Show, User } from '../db/entity';
import { MovieList } from '../db/entity/MovieList';
import { ShowList } from '../db/entity/ShowList';
import { InMemoryDb } from './fixtures/database';
import * as uuid from 'uuid/v4';
import DbAccess from '../db/DbAccess';


const should = chai.should();

describe('The DB', () => {
    const r = new random();

    let connection: Connection;

    let queryBuilder: QueryBuilder<User>;
    let userRepository: Repository<User>;
    let movieListRepository: Repository<MovieList>;

    let dbAccess: DbAccess;

    before(function() {
        this.timeout(10000);
        return new InMemoryDb('db.spec').connect().then(c => {
            connection = c;
            dbAccess = new DbAccess(connection);
        });
    });

    beforeEach(() => {
        userRepository = connection.getRepository(User);
        queryBuilder = userRepository.createQueryBuilder('user');
        movieListRepository = connection.getRepository(MovieList);
    });

    it('should insert and retrieve a user', async () => {
        let user = await generateUser();

        let foundUser = await queryBuilder.select().where('user.name = :name', { name: 'Gordon' }).getOne();
        chai.assert.exists(foundUser, 'foundUser exists');
    });

    it('should create a movie list for a user', async () => {
        let user = await generateUser();

        let show = new Show();
        show.name = 'Halt and Catch Fire';
        show.externalId = r.string(12);
        let showRet = await connection.getRepository(Show).save(show);

        let list = new MovieList();
        list.name = 'Test Movie List';
        list.user = Promise.resolve(user);
        list = await movieListRepository.save(list);

        let showList = new ShowList();
        showList.name = 'Test Show List';
        showList.user = Promise.resolve(user);
        showList.shows = [showRet];
        showList = await connection.getRepository(ShowList).save(showList);

        let userWithTrackedShows = await userRepository.findOne({
            where: {
                id: user.id
            },
            join: {
                alias: 'user',
                leftJoinAndSelect: {
                    'movieLists': 'user.movieLists',
                    'showLists': 'user.showLists',
                    'shows': 'showLists.shows'
                }
            }
        });

        chai.assert.exists(userWithTrackedShows, 'user exists')
        chai.assert.lengthOf(userWithTrackedShows.movieLists, 2);
        chai.assert.lengthOf(userWithTrackedShows.showLists, 2,);
        chai.assert.lengthOf(userWithTrackedShows.showLists[1].shows, 1, 'user show list has 1 tracked show');

        chai.assert.ownInclude(userWithTrackedShows.showLists[1].shows[0], { name: show.name, externalId: show.externalId }, 'user tracked show has the correct props');
    });

    async function generateUser(): Promise<User> {
        let user = new User('Gordon');
        user.username = uuid();
        user.email = uuid();
        user.password = '12345';

        return dbAccess.addUser(user);
    }
});
