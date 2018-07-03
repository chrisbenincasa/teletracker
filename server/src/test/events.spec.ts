import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import chaiSubset = require('chai-subset');
import { basename } from 'path';

import { Event, EventType, TargetEntityType } from '../db/entity';
import JwtVendor from '../util/JwtVendor';
import TestBase from './base';

chai.use(chaiHttp);
chai.use(chaiSubset);
const should = chai.should();

class EventsSpec extends TestBase {
    makeSpec(): Mocha.ISuite {
        let self = this;
        return describe('Events API', () => {
            before(async function () {
                this.timeout(self.timeout);
                await self.defaultBefore(true, true);
            });

            after(async function () {
                this.timeout(self.timeout);
                return self.defaultAfter();
            });

            it('should create and read events for a user', async () => {
                let user = await self.generateUser();
                let token = JwtVendor.vend(user.email);
                
                let result = await chai.request(self.baseServerUrl).get('/api/v1/users/self/events')
                    .set('Authorization', 'Bearer ' + token);

                result.body.data.should.have.lengthOf(0);

                let event = new Event();
                event.type = EventType.MarkedAsWatched;
                event.targetEntityType = TargetEntityType.Show;
                event.targetEntityId = 123;
                event.details = 'User marked Halt and Catch Fire as watched';
                
                let saveResult = await chai.request(self.baseServerUrl).
                    post('/api/v1/users/self/events').
                    set('Authorization', 'Bearer ' + token).
                    send({ event });

                let result2 = await chai.request(self.baseServerUrl).get('/api/v1/users/self/events').
                    set('Authorization', 'Bearer ' + token);

                
                result2.body.should.containSubset({
                    data: [
                        {
                            id: saveResult.body.data.id,
                            type: event.type,
                            details: event.details
                        }
                    ]
                });
            });
        });
    }
}

const scriptName = basename(__filename);
new EventsSpec(scriptName).makeSpec();