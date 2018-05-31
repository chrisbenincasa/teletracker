import * as chai from "chai";
import 'mocha';
import { QueryBuilder, Connection, Repository, AdvancedConsoleLogger } from "typeorm";
import { User, Show } from "../db/entity";
import { Db } from "../db/Connection";
import { MovieList } from "../db/entity/MovieList";
import { ShowList } from "../db/entity/ShowList";

const should = chai.should();

describe('The DB', () => {
    let connection: Connection;

    let queryBuilder: QueryBuilder<User>;
    let userRepository: Repository<User>;
    let movieListRepository: Repository<MovieList>;

    before(function() {
        this.timeout(10000);
        return new Db().connect().then(c => {
            connection = c;
        });
    });

    beforeEach(() => {
        userRepository = connection.getRepository(User);
        queryBuilder = userRepository.createQueryBuilder('user');
        movieListRepository = connection.getRepository(MovieList);
    });

    it('should insert and retrieve a user', async () => {
        let user = new User('Christian');
        await userRepository.save(user);

        let foundUser = await queryBuilder.select().where('user.name = :name', { name: 'Christian' }).getOne();
        chai.assert.exists(foundUser, 'foundUser exists');
    });

    it('should create a movie list for a user', async () => {
        let user = new User('TestList');
        let userRet = await userRepository.save(user);

        let show = new Show();
        show.name = 'Halt and Catch Fire';
        show.externalId = '123';
        let showRet = await connection.getRepository(Show).save(show);

        let list = new MovieList();
        list.user = user;
        let listRet = await movieListRepository.save(list);

        let showList = new ShowList();
        showList.user = user;
        showList.shows = [showRet];
        let showListRet = await connection.getRepository(ShowList).save(showList);

        let userWithTrackedShows = await userRepository.findOne({
            where: {
                id: userRet.id
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
        chai.assert.lengthOf(userWithTrackedShows.movieLists, 1, 'user has 1 movie list');
        chai.assert.lengthOf(userWithTrackedShows.showLists, 1, 'user has 1 show list');
        chai.assert.lengthOf(userWithTrackedShows.showLists[0].shows, 1, 'user show list has 1 tracked show');

        chai.assert.ownInclude(userWithTrackedShows.showLists[0].shows[0], { name: 'Halt and Catch Fire', externalId: '123' }, 'user tracked show has the correct props');
    });
});
