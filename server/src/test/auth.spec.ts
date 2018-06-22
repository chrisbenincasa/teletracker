import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');

import JwtVendor from '../util/JwtVendor';
import TestBase from './base';

chai.use(chaiHttp);

class AuthSpec extends TestBase {
    makeSpec(): Mocha.ISuite {
        let self = this;

        return describe('Authentication API', () => {
            let token1: string;

            before(async function () {
                this.timeout(self.timeout);
                await self.defaultBefore(true, true);
                
                let response = await chai.request(self.baseServerUrl).
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
                this.timeout(self.timeout);
                await self.defaultAfter();
            });

            it('should create and authorize a user', async () => {
                let authResponse = await chai.request(self.baseServerUrl).
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

                let authResponse = await chai.request(self.baseServerUrl).
                    get('/api/v1/auth/status').
                    set('Authorization', 'Bearer ' + expiredToken).
                    send();

                chai.expect(authResponse).to.be.json;
                chai.expect(authResponse).to.have.status(401)
                chai.expect(authResponse.body).to.have.key('error');
            });
        });
    }
}

import { basename } from 'path';
const scriptName = basename(__filename);

new AuthSpec(scriptName).makeSpec();