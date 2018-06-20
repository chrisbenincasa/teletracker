import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import chaiSubset = require('chai-subset');
import * as random from 'random-js';
import { Connection } from 'typeorm';
import { inspect } from 'util';
import * as uuid from 'uuid/v4';

import { GlobalConfig } from '../Config';
import DbAccess from '../db/DbAccess';
import { MovieList, Show, ShowList, User } from '../db/entity';
import Server from '../Server';
import JwtVendor from '../util/JwtVendor';
import { InMemoryDb } from './fixtures/database';

chai.use(chaiHttp);
chai.use(chaiSubset);
const should = chai.should();

describe('Users API', () => {
    const r = new random();
    let server: Server;
    let connection: Connection;
    const baseUrl = `http://localhost:3000`;
    let dbAccess: DbAccess;

    before(async function () {
        this.timeout(10000);
        let db = new InMemoryDb('users_api');
        server = new Server(GlobalConfig, db);
        await server.main();
        connection = await db.connect();
        dbAccess = new DbAccess(connection);
    });

    after(function (done) {
        server.instance.close(() => done());
    });

    it('should create default lists for a new user', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let res = await chai.request(baseUrl).get(`/api/v1/users/${user.id}`).set('Authorization', 'Bearer ' + token);

        res.body.data.showLists.should.have.lengthOf(1);
        res.body.data.movieLists.should.have.lengthOf(1);

        res.body.should.containSubset({
            data: {
                name: user.name,
                email: user.email,
                username: user.username,
                id: user.id,
                movieLists: [{
                    id: user.movieLists[0].id,
                    name: user.movieLists[0].name,
                    createdAt: user.movieLists[0].createdAt.toISOString(),
                    updatedAt: user.movieLists[0].updatedAt.toISOString(),
                    isDefault: true,
                    isDeleted: false
                }],
                showLists: [{
                    id: user.showLists[0].id,
                    name: user.showLists[0].name,
                    createdAt: user.showLists[0].createdAt.toISOString(),
                    updatedAt: user.showLists[0].updatedAt.toISOString(),
                    isDefault: true,
                    isDeleted: false
                }]
            }
        });
    });

    it('should retrieve all lists for a user', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let showList = new ShowList();
        showList.user = Promise.resolve(user);
        showList.name = 'Test Show List';
        await connection.getRepository(ShowList).save(showList);

        let movieList = new MovieList();
        movieList.user = Promise.resolve(user);
        movieList.name = 'Test Movie List';
        await connection.getRepository(MovieList).save(movieList);

        let res = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists`).
            set('Authorization', 'Bearer ' + token);

        res.type.should.equal('application/json');
        res.body.should.include.keys('data');

        chai.assert.ownInclude(res.body.data, { id: user.id, name: user.name });
        chai.assert.lengthOf(res.body.data.showLists, 2); // New users get a default list
        chai.assert.lengthOf(res.body.data.movieLists, 2); // New users get a default list
    });

    it('should respond with a user\'s specific list', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let show = new Show();
        show.name = 'Halt and Catch Fire';
        show.externalId = r.string(12);
        let showRet = await connection.getRepository(Show).save(show);

        let showList = new ShowList();
        showList.user = Promise.resolve(user);
        showList.name = 'Test Show List';
        showList.shows = [showRet];
        showList = await connection.getRepository(ShowList).save(showList);

        let res2 = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/shows/${showList.id}`).
            set('Authorization', 'Bearer ' + token);

        res2.type.should.equal('application/json');
        res2.body.should.include.keys('data');
        res2.body.should.containSubset({
            data: {
                id: showList.id,
                name: showList.name,
                isDefault: false,
                shows: []
            }
        });
    });

    it('should respond with 404 when a user\'s specific list cannot be found', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let response = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/shows/1000000`).
            set('Authorization', 'Bearer ' + token).
            send();

        chai.expect(response).to.have.status(404);
    });

    it('should add a show to a user\'s show list', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        // Create a show
        let show = new Show();
        show.name = 'Halt and Catch Fire';
        show.externalId = r.string(12);
        show = await connection.getRepository(Show).save(show);

        // Create a list
        let showList = new ShowList();
        showList.user = Promise.resolve(user);
        showList.name = 'Test Show List';
        showList = await connection.getRepository(ShowList).save(showList);

        let response = await chai.
            request(baseUrl).
            put(`/api/v1/users/${user.id}/lists/shows/${showList.id}/tracked`).
            set('Authorization', 'Bearer ' + token).
            send({ showId: show.id });

        chai.expect(response).to.have.status(200);

        // Now get the lists
        let userAndList = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/shows/${showList.id}`).
            set('Authorization', 'Bearer ' + token);;

        chai.expect(userAndList.body.data.shows).to.deep.equal([
            {
                id: show.id,
                externalId: show.externalId,
                name: show.name,
                externalSource: show.externalSource,
                type: 'show'
            }
        ]);
    });

    it('should create a show list for a user', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let response = await chai.
            request(baseUrl).
            post(`/api/v1/users/${user.id}/lists`).
            set('Authorization', 'Bearer ' + token).
            send({ type: 'show', name: 'Test Show List' });
    
        chai.expect(response).to.be.json;
        chai.expect(response.body).to.have.key('data');
        chai.expect(response.body.data).to.have.key('id');

        // Retrieve the list for the user to assert the association
        let userList = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/shows/${response.body.data.id}`).
            set('Authorization', 'Bearer ' + token);

        chai.expect(userList).to.be.json;
        chai.expect(userList.body).to.containSubset({
            data: {
                id: response.body.data.id,
                shows: [],
                name: 'Test Show List',
                isDefault: false,
                isDeleted: false
            }
        });
    });

    async function generateUser(): Promise<User> {
        let user = new User('Gordon');
        user.username = uuid();
        user.email = uuid();
        user.password = '12345';

        return dbAccess.addUser(user);
    }
});