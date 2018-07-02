import * as R from 'ramda';
import { Repository, EntityRepository } from "typeorm";
import { TvShowEpisode, ExternalSource, ThingExternalIds } from "./entity";
import { Optional } from "../util/Types";

@EntityRepository(TvShowEpisode)
export class TvEpisodeRepository extends Repository<TvShowEpisode> {
    async insertOrUpdate(episode: TvShowEpisode) {
        return this.createQueryBuilder().
            insert().
            values(episode).
            onConflict('("seasonId", "number") DO NOTHING').
            returning('id').
            execute().
            then(res => {
                if (res.identifiers && res.identifiers.length > 0 && res.identifiers[0]) {
                    episode.id = res.identifiers[0].id;
                }
                return episode;
            });
    }

    async findByExternalId(externalSource: ExternalSource, externalId: string): Promise<Optional<TvShowEpisode>> {
        return this.manager.createQueryBuilder(ThingExternalIds, 'externalIds').
            where({ externalSource, externalId }).
            leftJoinAndSelect('externalIds.tvEpisode', 'tvEpisode').
            getOne().
            then(x => x ? x.tvEpisode : null);
    }
}