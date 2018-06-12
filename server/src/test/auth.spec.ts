import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import * as random from 'random-js';

import { GlobalConfig } from '../Config';
import Server from '../Server';
import JwtVendor from '../util/JwtVendor';
import { InMemoryDb } from './fixtures/database';

chai.use(chaiHttp);

describe('Authentication API', () => {
    const r = new random();
    let server: Server;
    const baseUrl = `http://localhost:3000`;

    let token1: string;

    before(async function () {
        this.timeout(10000);
        let db = new InMemoryDb('users_api');
        server = new Server(GlobalConfig, db);
        await server.main();

        let response = await chai.request(baseUrl).
            post(`/api/v1/users`).
            send({
                name: 'Gordon',
                username: 'gordo',
                email: 'gordon@hacf.com',
                password: '12345'
            });

        let { data: { token } } = response.body;
        token1 = token;
    });

    after(function (done) {
        server.instance.close(() => done());
    });

    it('should create and authorize a user', async () => {
        let authResponse = await chai.request(baseUrl).
            get('/api/v1/auth/status').
            set('Authorization', 'Bearer ' + token1).
            send();

        chai.expect(authResponse).to.be.json;
        chai.expect(authResponse.body).to.deep.equal({
            data: {
                authenticated: true,
                email: 'gordon@hacf.com'
            }
        });
    });

    it('should block an auth-required endpoint on invalid token', async () => {
        let expiredToken = JwtVendor.vend('gordon@hacf.com', Math.floor(Date.now() / 1000) - (60 * 60)) // Token expired an hour ago.

        let authResponse = await chai.request(baseUrl).
            get('/api/v1/auth/status').
            set('Authorization', 'Bearer ' + expiredToken).
            send();

        chai.expect(authResponse).to.be.json;
        chai.expect(authResponse).to.have.status(401)
        chai.expect(authResponse.body).to.have.key('error');
    });
});