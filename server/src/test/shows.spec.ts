import 'mocha';

import * as chai from 'chai';
import chaiHttp = require('chai-http');
import chaiSubset = require('chai-subset');
import { basename } from 'path';
import { getManager } from 'typeorm';

import { Availability, ExternalSource, Network, Thing, ThingType } from '../db/entity';
import { TvShowEpisode } from '../db/entity/TvShowEpisode';
import { TvShowSeason } from '../db/entity/TvShowSeason';
import TestBase from './base';

chai.use(chaiHttp);
chai.use(chaiSubset);
const should = chai.should();

class TvShowSpec extends TestBase {
    makeSpec(): Mocha.ISuite {
        let self = this;

        return describe('TV Show APIs', async () => {
            before(async function () {
                this.timeout(self.timeout);
                await self.defaultBefore(true, true);
            });

            after(async function () {
                this.timeout(self.timeout);
                self.defaultAfter();
            });

            it('should do soemthing', async () => {
                let manager = getManager(self.connection.name);

                let season = manager.create(TvShowSeason, {
                    number: 1
                });
                season = await manager.save(season);

                let episode = manager.create(TvShowEpisode, {
                    number: 1,
                    season,
                    name: 'The One Where They All Die',
                });
                episode = await manager.save(episode);

                let show = manager.create(Thing, {
                    name: 'Not Friends',
                    normalizedName: 'not-friends',
                    type: ThingType.Show,
                    seasons: [season]
                });
                show = await manager.save(show);

                let network = manager.create(Network, {
                    homepage: "https://netflix.com",
                    name: 'Netflix',
                    origin: 'US',
                    externalId: '123',
                    externalSource: ExternalSource.TheMovieDb
                });
                network = await manager.save(Network, network);

                let availability = manager.create(Availability, {
                    tvShowEpisode: episode,
                    network: network,
                    region: 'US',
                    isAvailable: true
                });
                availability = await manager.save(availability);

                let res = await this.thingRepository.getShowById(show.id);

                res.should.containSubset({
                    id: show.id,
                    name: show.name,
                    normalizedName: show.normalizedName,
                    seasons: [{
                        id: season.id,
                        number: season.number,
                        episodes: [{
                            id: episode.id,
                            number: episode.number,
                            availability: [{
                                id: availability.id,
                                isAvailable: true,
                                network: {
                                    name: network.name,
                                    externalSource: ExternalSource.TheMovieDb
                                }
                            }]
                        }]
                    }]
                });
            });
        });
    }
}

const scriptName = basename(__filename);

new TvShowSpec(scriptName).makeSpec();