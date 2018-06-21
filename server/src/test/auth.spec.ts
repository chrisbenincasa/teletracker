import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import * as R from 'ramda';
import * as random from 'random-js';
import { Connection } from 'typeorm';

import { GlobalConfig } from '../Config';
import DbAccess from '../db/DbAccess';
import Server from '../Server';
import JwtVendor from '../util/JwtVendor';
import PostgresDocker from './fixtures/docker';
import { getRandomPort } from './util';

chai.use(chaiHttp);

describe('Authentication API', () => {
    const r = new random();
    let server: Server;
    let port = getRandomPort();
    const baseUrl = `http://localhost:${port}`;
    let dbAccess: DbAccess;
    let docker: PostgresDocker;
    let connection: Connection;

    let token1: string;

    before(async function () {
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
                name: 'auth.spec.ts'
            }
        });

        server = new Server(config);
        await server.main().catch(console.error);
        connection = server.connection;
        dbAccess = new DbAccess(connection);

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

    after(async function () {
        this.timeout(10000);
        if (server.instance) {
            await server.instance.close(async () => {
                await docker.stopDb();
            });
        } else {
            await docker.stopDb();
        }
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