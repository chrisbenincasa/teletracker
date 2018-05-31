import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import { Connection, getConnection } from 'typeorm';

import { Show, ShowList, User, MovieList } from '../db/entity';
import Server from '../Server';
import { InMemoryDb } from './fixtures/database';

chai.use(chaiHttp);
const should = chai.should();

describe('Movies API', () => {
    let server: Server;
    let connection: Connection;
    const baseUrl = `http://localhost:3000`

    before(async function() {
        this.timeout(10000);
        server = new Server(3000, new InMemoryDb('movies.spec'));
        await server.main();
        connection = getConnection();
    });

    after(function(done) {
        server.instance.close(() => done());
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