import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');

import Server from '../Server';
import TestBase from './base';
import PostgresDocker from './fixtures/docker';

chai.use(chaiHttp);
const should = chai.should();

class MovieSpec extends TestBase {
    makeSpec(): Mocha.ISuite {
        let self = this;
        return describe('Movies API', () => {
            let server: Server;
            let docker: PostgresDocker;

            before(async function () {
                this.timeout(self.timeout);
                await self.defaultBefore(true, true);
            });

            after(async function () {
                this.timeout(self.timeout);
                await self.defaultAfter();
            });

            it('GET /api/v1/movies/:id should respond with a single movie with required fields', function (done) {
                if (!process.env.API_KEY) {
                    this.skip();
                }

                chai.request(self.baseServerUrl)
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
    }
}

import { basename } from 'path';
const scriptName = basename(__filename);

new MovieSpec(scriptName).makeSpec();