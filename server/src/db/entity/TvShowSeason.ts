import { Entity, PrimaryGeneratedColumn, ManyToOne, OneToMany, Column, Unique, JoinColumn } from 'typeorm';
import { Thing } from './Thing';
import { TvShowEpisode } from './TvShowEpisode';

@Entity()
@Unique(['show', 'number'])
export class TvShowSeason {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    number: number;

    @ManyToOne(type => Thing)
    show: Thing;

    @OneToMany(type => TvShowEpisode, ep => ep.season)
    @JoinColumn()
    episodes: TvShowEpisode[]
}