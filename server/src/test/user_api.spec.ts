import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import { Connection, getConnection, createConnection } from 'typeorm';

import { Show, ShowList, User, MovieList } from '../db/entity';
import Server from '../Server';
import { InMemoryDb } from './fixtures/database';
import * as random from 'random-js';

chai.use(chaiHttp);
const should = chai.should();

describe('Users API', () => {
    const r = new random();
    let server: Server;
    let connection: Connection;
    const baseUrl = `http://localhost:3000`

    before(async function () {
        this.timeout(10000);
        let db = new InMemoryDb('users_api');
        server = new Server(3000, db);
        await server.main();
        connection = await db.connect();
    });

    after(function (done) {
        server.instance.close(() => done());
    });

    it('should retrieve all lists for a user', async () => {
        let user = new User('Gordon');
        let userRet = await connection.getRepository(User).save(user);

        let showList = new ShowList();
        showList.user = user;
        let showListRet = await connection.getRepository(ShowList).save(showList);

        let movieList = new MovieList();
        movieList.user = user;
        let movieListRet = await connection.getRepository(MovieList).save(movieList);

        let res = await chai.request(baseUrl).get(`/api/v1/users/${userRet.id}/lists`);

        res.type.should.equal('application/json');
        res.body.should.include.keys('data');

        chai.assert.ownInclude(res.body.data, { id: userRet.id, name: userRet.name });
        chai.assert.lengthOf(res.body.data.showLists, 1);
        chai.assert.lengthOf(res.body.data.movieLists, 1);
    });

    it('should respond with a user\'s specific list', async () => {
        let user = new User('Gordon');
        let userRet = await connection.getRepository(User).save(user);

        let show = new Show();
        show.name = 'Halt and Catch Fire';
        show.externalId = r.string(12);
        let showRet = await connection.getRepository(Show).save(show);

        let showList = new ShowList();
        showList.user = user;
        showList.shows = [showRet];
        let showListRet = await connection.getRepository(ShowList).save(showList);

        let res2 = await chai.request(baseUrl).get(`/api/v1/users/${userRet.id}/lists/shows/${showList.id}`)

        res2.type.should.equal('application/json');
        res2.body.should.include.keys('data');
        res2.body.data.should.include.keys('name', 'id', 'showLists');
    });

    it('should create a show list for a user', async () => {
        let user = new User('Whatever');
        user = await connection.getRepository(User).save(user);

        let response = await chai.
            request(baseUrl).
            post(`/api/v1/users/${user.id}/lists`).
            send({ type: 'show' });
        
        chai.expect(response).to.be.json;
        chai.expect(response.body).to.have.key('data');
        chai.expect(response.body.data).to.have.key('id');

        // Retrieve the list for the user to assert the association
        let userList = await chai.request(baseUrl).get(`/api/v1/users/${user.id}/lists/shows/${response.body.data.id}`);

        chai.expect(userList).to.be.json;
        chai.expect(userList.body).to.deep.equal({
            data: {
                name: user.name,
                id: user.id,
                showLists: [
                    { id: response.body.data.id }
                ]
            }
        });
    });
});