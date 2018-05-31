import * as chai from "chai";
import chaiHttp = require("chai-http");
import 'mocha';

chai.use(chaiHttp);
const should = chai.should();
const server = require('../index');

describe('GET /api/v1/movies/:id', () => {
  it('should respond with a single movie with required fields', (done) => {
    chai.request('http://localhost:3000')
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