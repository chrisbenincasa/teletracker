import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import chaiSubset = require('chai-subset');
import { getManager } from 'typeorm';

import { Availability, Network, Thing, ThingType } from '../db/entity';
import TestBase from './base';

chai.use(chaiHttp);
chai.use(chaiSubset);
const should = chai.should();

class AvailabilitySpec extends TestBase {
    makeSpec(): Mocha.ISuite {
        let self = this;
        return describe("Availability information", async () => {
            before(async function() {
                this.timeout(self.timeout);
                await self.defaultBefore(true, true);
            });

            after(async function() {
                this.timeout(self.timeout);
                return self.defaultAfter();
            });

            it('should make availabilities', async () => {
                let manager = getManager(self.connection.name);

                let show = manager.create(Thing, {
                    name: 'Halt and Catch Fire',
                    normalizedName: 'halt-and-catch-fire',
                    type: ThingType.Show
                });
                show = await manager.save(show);

                let network = manager.create(Network, {
                    homepage: "https://netflix.com",
                    name: 'Netflix',
                    origin: 'US',
                    slug: 'netflix',
                    shortname: 'netflix'
                });
                network = await manager.save(Network, network);

                let availability = manager.create(Availability, {
                    thing: show,
                    network: network,
                    isAvailable: true,
                    region: 'US'
                });
                availability = await manager.save(Availability, availability);

                let loadedShow = await chai.request(self.baseServerUrl).get(`/api/v1/shows/${show.id}`).send();

                loadedShow.body.should.containSubset({
                    data: {
                        id: show.id,
                        name: show.name,
                        normalizedName: show.normalizedName,
                        type: show.type,
                        availability: [{
                            id: availability.id,
                            isAvailable: true,
                            region: 'US',
                            network: {
                                // id: network.id,
                                name: network.name
                                // homepage: network.homepage
                            }
                        }]
                    }
                });
            });
        });
    }
}

const scriptName = require('path').basename(__filename);

new AvailabilitySpec(scriptName).makeSpec();