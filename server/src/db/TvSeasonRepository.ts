import { EntityRepository, Repository } from 'typeorm';

import { TvShowSeason, Thing } from './entity';
import R = require('ramda');

@EntityRepository(TvShowSeason)
export class TvSeasonRepository extends Repository<TvShowSeason> {
    async getAllForShow(show: Thing): Promise<TvShowSeason[]> {
        return this.manager.createQueryBuilder(Thing, 'thing').
            where({ id: show.id }).
            leftJoinAndSelect('thing.seasons', 'seasons').
            getOne().
            then(R.prop('seasons'));
    }

    
}