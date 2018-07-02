import { Entity, PrimaryGeneratedColumn, OneToOne, Column, JoinColumn, Index, Unique, UpdateDateColumn } from "typeorm";
import { Thing } from "./Thing";
import { TvShowEpisode } from "./TvShowEpisode";

@Entity('thing_external_ids')
@Unique(['thing'])
@Unique(['tvEpisode'])
export class ThingExternalIds {
    @PrimaryGeneratedColumn()
    id: string

    @OneToOne(type => Thing, t => t.externalIds, { nullable: true, cascade: ['insert', 'update'] })
    @JoinColumn({ name: 'thingId' })
    thing?: Thing

    @OneToOne(type => TvShowEpisode, t => t.externalIds, { nullable: true, cascade: ['insert', 'update'] })
    @JoinColumn({ name: 'tvEpisodeId' })
    tvEpisode?: TvShowEpisode

    @Column({ nullable: true })
    @Index({ unique: true })
    tmdbId?: string

    @Column({ nullable: true })
    @Index()
    imdbId?: string

    @Column({ nullable: true })
    @Index()
    netflixId: string

    @UpdateDateColumn()
    lastUpdatedAt: Date
}