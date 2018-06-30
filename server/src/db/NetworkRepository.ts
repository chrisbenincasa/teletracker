import * as R from 'ramda';
import { EntityRepository, Repository, FindManyOptions, In } from 'typeorm';

import * as Entity from './entity';
import { Optional } from '../util/Types';

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

    async findNetworkBySlug(slug: string): Promise<Optional<Entity.Network>> {
        return this.findOne({ where: { slug }});
    }

    async findNetworksBySlugs(slugs: Set<string>): Promise<Map<string, Entity.Network>> {
        if (slugs.size == 0) {
            return Promise.resolve(new Map);
        }

        return this.find({ 
            where: { 
                slug: In(Array.from(slugs))
            }
        }).then(networks => {
            let pairs: [string, Entity.Network][] = networks.map(n => [n.slug, n] as [string, Entity.Network]);
            return new Map<string, Entity.Network>(pairs);
        });
    }

    // async findNetworkByExternalSource()
}