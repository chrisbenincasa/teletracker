import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import chaiSubset = require('chai-subset');
import { basename } from 'path';
import { Connection } from 'typeorm';
import * as uuid from 'uuid/v4';

import DbAccess from '../db/DbAccess';
import { List, Thing, ThingType, User } from '../db/entity';
import JwtVendor from '../util/JwtVendor';
import TestBase from './base';

chai.use(chaiHttp);
chai.use(chaiSubset);
const should = chai.should();

class UserApiSpec extends TestBase {
    connection: Connection
    dbAccess: DbAccess

    makeSpec() {
        let self = this;
        return describe('Users API', () => {
            before(async function () {
                this.timeout(self.timeout);
                await self.defaultBefore(true, true);
                self.connection = self.server.connection;
                self.dbAccess = new DbAccess(self.connection);
            });

            after(async function () {
                this.timeout(self.timeout);
                self.defaultAfter();
            });

            it('should create default lists for a new user', async () => {
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);

                let res = await chai.request(self.baseServerUrl).get(`/api/v1/users/${user.id}`).set('Authorization', 'Bearer ' + token);

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
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);

                let showList = new List();
                showList.user = Promise.resolve(user);
                showList.name = 'Test Show List';
                await self.connection.getRepository(List).save(showList);

                let res = await chai.request(self.baseServerUrl).
                    get(`/api/v1/users/${user.id}/lists`).
                    set('Authorization', 'Bearer ' + token);

                res.type.should.equal('application/json');
                res.body.should.include.keys('data');

                chai.assert.ownInclude(res.body.data, { id: user.id, name: user.name });
                chai.assert.lengthOf(res.body.data.lists, 2); // New users get a default list
            });

            it('should respond with a user\'s specific list', async () => {
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);

                let show = new Thing();
                show.name = 'Halt and Catch Fire';
                show.normalizedName = 'halt-and-catch-fire';
                show.type = ThingType.Show;
                // show.externalId = r.string(12);
                let showRet = await self.connection.getRepository(Thing).save(show);

                let showList = new List();
                showList.user = Promise.resolve(user);
                showList.name = 'Test Show List';
                showList.things = [showRet];
                showList = await self.connection.getRepository(List).save(showList);

                let res2 = await chai.request(self.baseServerUrl).
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
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);

                let response = await chai.request(self.baseServerUrl).
                    get(`/api/v1/users/${user.id}/lists/1000000`).
                    set('Authorization', 'Bearer ' + token).
                    send();

                chai.expect(response).to.have.status(404);
            });

            it('should add a show to a user\'s show list', async () => {
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);

                // Create a show
                let show = new Thing();
                show.name = 'Halt and Catch Fire 2';
                show.normalizedName = 'halt-and-catch-fire-2';
                show.type = ThingType.Show;
                // show.externalId = r.string(12);
                show = await self.connection.getRepository(Thing).save(show);

                // Create a list
                let list = new List();
                list.user = Promise.resolve(user);
                list.name = 'Test Show ListXYZ';
                list = await self.connection.getRepository(List).save(list);

                let response = await chai.
                    request(self.baseServerUrl).
                    put(`/api/v1/users/${user.id}/lists/${list.id}/tracked`).
                    set('Authorization', 'Bearer ' + token).
                    send({ itemId: show.id });

                chai.expect(response).to.have.status(200);

                // Now get the lists
                let userAndList = await chai.request(self.baseServerUrl).
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
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);

                let response = await chai.
                    request(self.baseServerUrl).
                    post(`/api/v1/users/${user.id}/lists`).
                    set('Authorization', 'Bearer ' + token).
                    send({ type: 'show', name: 'Test Show List' });

                chai.expect(response).to.be.json;
                chai.expect(response.body).to.have.key('data');
                chai.expect(response.body.data).to.have.key('id');

                // Retrieve the list for the user to assert the association
                let userList = await chai.request(self.baseServerUrl).
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
        });
    }
}

const scriptName = basename(__filename);

new UserApiSpec(scriptName).makeSpec();