import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import * as R from 'ramda';

import { GlobalConfig } from '../Config';
import Server from '../Server';
import PostgresDocker from './fixtures/docker';
import { getRandomPort } from './util';

chai.use(chaiHttp);
const should = chai.should();

describe('Movies API', () => {
    let server: Server;
    let port = getRandomPort()
    const baseUrl = `http://localhost:${port}`

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
                name: 'movie.spec.ts'
            }
        });

        server = new Server(config);
        await server.main().catch(console.error);
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

    it('GET /api/v1/movies/:id should respond with a single movie with required fields', function(done) {
        if (!process.env.API_KEY) {
            this.skip();
        }

        chai.request(baseUrl)
            // chai.request(server)
            .get('/api/v1/movies/123')
            .end((err, res) => {
                // there should be no errors
                should.not.exist(err);
                // there should be a 200 status code
                res.status.should.equal(200);
                // the response should be JSON
                res.type.should.equal('application/json');
                // the JSON response body should have a
                // key-value pair of {"status": "success"}

                // commenting out as status is not currently provided via the controllers
                // res.body.status.should.eql('success');

                // the JSON response body should have a
                // key-value pair of {"data": 1 movie object}
                res.body.should.include.keys(
                    'id', 'title', 'genres', 'vote_average', 'adult'
                );
                done();
            });
    });
});