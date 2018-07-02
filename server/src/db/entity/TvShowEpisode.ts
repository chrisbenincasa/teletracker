import { Column, Entity, ManyToOne, OneToMany, PrimaryGeneratedColumn, OneToOne, JoinColumn, CreateDateColumn, UpdateDateColumn, Unique } from 'typeorm';

import { Availability } from './Availability';
import { TvShowSeason } from './TvShowSeason';
import { ThingExternalIds } from './ThingExternalIds';

@Entity()
@Unique(['season', 'number'])
export class TvShowEpisode {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    number: number;

    @ManyToOne(type => TvShowSeason)
    season: TvShowSeason

    @Column({ nullable: true })
    name?: string

    @Column({ nullable: true })
    productionCode?: string;

    @OneToMany(type => Availability, av => av.tvShowEpisode)
    availability: Availability[]

    @OneToOne(t => ThingExternalIds, t => t.tvEpisode)
    externalIds: ThingExternalIds

    @CreateDateColumn()
    createdAt: Date

    @UpdateDateColumn()
    lastUpdatedAt: Date
}