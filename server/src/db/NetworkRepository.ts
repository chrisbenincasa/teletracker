import * as R from 'ramda';
import { EntityRepository, Repository, FindManyOptions } from 'typeorm';

import * as Entity from './entity';

@EntityRepository(Entity.Network)
export class NetworkRepository extends Repository<Entity.Network> {
    async saveNetwork(network: Entity.Network): Promise<Entity.Network> {
        return this.manager.save(Entity.Network, network);
    }

    async getNetworks(id?: number, loadAvailability?: boolean): Promise<Entity.Network[]> {
        let options: FindManyOptions = null;

        if (id) {
            options = { where: { id } };
        }

        if (loadAvailability) {
            let joinOpts: FindManyOptions = {
                join: {
                    alias: 'network',
                    leftJoinAndSelect: {
                        availability: 'network.availability'
                    }
                }
            };

            options = R.mergeDeepRight(options, joinOpts);
        }

        return this.manager.find(Entity.Network, options);
    }
}