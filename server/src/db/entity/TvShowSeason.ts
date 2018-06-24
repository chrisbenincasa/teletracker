import { Entity, PrimaryGeneratedColumn, ManyToOne, OneToMany, Column } from 'typeorm';
import { Thing } from './Thing';
import { TvShowEpisode } from './TvShowEpisode';

@Entity()
export class TvShowSeason {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    number: number;

    @ManyToOne(type => Thing)
    show: Thing;

    @OneToMany(type => TvShowEpisode, ep => ep.season)
    episodes: TvShowEpisode[]
}