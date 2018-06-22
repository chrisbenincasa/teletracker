import { Column, Entity, ManyToOne, OneToMany, PrimaryGeneratedColumn } from 'typeorm';

import { Availability } from './Availability';
import { TvShowSeason } from './TvShowSeason';

@Entity()
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
}