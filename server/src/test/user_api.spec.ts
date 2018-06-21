import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import chaiSubset = require('chai-subset');
import * as R from 'ramda';
import * as random from 'random-js';
import { Connection } from 'typeorm';
import * as uuid from 'uuid/v4';

import { GlobalConfig } from '../Config';
import DbAccess from '../db/DbAccess';
import { List, Thing, User, ThingType } from '../db/entity';
import Server from '../Server';
import JwtVendor from '../util/JwtVendor';
import PostgresDocker from './fixtures/docker';
import { getRandomPort } from './util';

chai.use(chaiHttp);
chai.use(chaiSubset);
const should = chai.should();

describe('Users API', () => {
    const r = new random();
    let server: Server;
    let connection: Connection;
    let port = getRandomPort();
    const baseUrl = `http://localhost:${port}`;
    let dbAccess: DbAccess;
    let docker: PostgresDocker;

    before(async function() {
        this.timeout(10000);
        docker = new PostgresDocker();
        await docker.client.ping();
        try {
            await docker.startDb();
        } catch (e) {
            console.error(e);
        }

        let config = R.mergeDeepRight(GlobalConfig, {
            server: {
                port
            },
            db: {
                type: 'postgres',
                host: 'localhost',
                port: docker.boundPort,
                password: 'teletracker',
                name: 'user_api.spec.ts'
            }
        });

        server = new Server(config);
        await server.main().catch(console.error);
        connection = server.connection;
        dbAccess = new DbAccess(connection);
    });

    after(async function() {
        this.timeout(10000);
        if (server.instance) {
            await server.instance.close(async () => {
                await docker.stopDb();
            });
        } else {
            await docker.stopDb();
        }
    });

    it('should create default lists for a new user', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let res = await chai.request(baseUrl).get(`/api/v1/users/${user.id}`).set('Authorization', 'Bearer ' + token);

        res.body.data.lists.should.have.lengthOf(1);

        res.body.should.containSubset({
            data: {
                name: user.name,
                email: user.email,
                username: user.username,
                id: user.id,
                lists: [{
                    id: user.lists[0].id,
                    name: user.lists[0].name,
                    createdAt: user.lists[0].createdAt.toISOString(),
                    updatedAt: user.lists[0].updatedAt.toISOString(),
                    isDefault: true,
                    isDeleted: false
                }]
            }
        });
    });

    it('should retrieve all lists for a user', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let showList = new List();
        showList.user = Promise.resolve(user);
        showList.name = 'Test Show List';
        await connection.getRepository(List).save(showList);

        let res = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists`).
            set('Authorization', 'Bearer ' + token);

        res.type.should.equal('application/json');
        res.body.should.include.keys('data');

        chai.assert.ownInclude(res.body.data, { id: user.id, name: user.name });
        chai.assert.lengthOf(res.body.data.lists, 2); // New users get a default list
    });

    it('should respond with a user\'s specific list', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let show = new Thing();
        show.name = 'Halt and Catch Fire';
        show.normalizedName = 'halt-and-catch-fire';
        show.type = ThingType.Show;
        // show.externalId = r.string(12);
        let showRet = await connection.getRepository(Thing).save(show);

        let showList = new List();
        showList.user = Promise.resolve(user);
        showList.name = 'Test Show List';
        showList.things = [showRet];
        showList = await connection.getRepository(List).save(showList);

        let res2 = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/${showList.id}`).
            set('Authorization', 'Bearer ' + token);

        res2.type.should.equal('application/json');
        res2.body.should.include.keys('data');
        res2.body.should.containSubset({
            data: {
                id: showList.id,
                name: showList.name,
                isDefault: false,
                things: []
            }
        });
    });

    it('should respond with 404 when a user\'s specific list cannot be found', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        let response = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/1000000`).
            set('Authorization', 'Bearer ' + token).
            send();

        chai.expect(response).to.have.status(404);
    });

    it('should add a show to a user\'s show list', async () => {
        let user = await generateUser();
        let token = JwtVendor.vend(user.email);

        // Create a show
        let show = new Thing();
        show.name = 'Halt and Catch Fire 2';
        show.normalizedName = 'halt-and-catch-fire-2';
        show.type = ThingType.Show;
        // show.externalId = r.string(12);
        show = await connection.getRepository(Thing).save(show);

        // Create a list
        let list = new List();
        list.user = Promise.resolve(user);
        list.name = 'Test Show ListXYZ';
        list = await connection.getRepository(List).save(list);

        let response = await chai.
            request(baseUrl).
            put(`/api/v1/users/${user.id}/lists/${list.id}/tracked`).
            set('Authorization', 'Bearer ' + token).
            send({ itemId: show.id });

        chai.expect(response).to.have.status(200);

        // Now get the lists
        let userAndList = await chai.request(baseUrl).
            get(`/api/v1/users/${user.id}/lists/${list.id}`).
            set('Authorization', 'Bearer ' + token);

        chai.expect(userAndList.body.data.things).to.containSubset([
            {
                id: show.id,
                // externalId: show.externalId,
                name: show.name,
                // externalSource: show.externalSource,
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
            get(`/api/v1/users/${user.id}/lists/${response.body.data.id}`).
            set('Authorization', 'Bearer ' + token);

        chai.expect(userList).to.be.json;
        chai.expect(userList.body).to.containSubset({
            data: {
                id: response.body.data.id,
                things: [],
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