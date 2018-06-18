import { Entity, PrimaryGeneratedColumn, Column, ManyToMany, JoinTable, Index } from 'typeorm';
import { ExternalSource } from './MediaItem';
import { Show } from './Show';
import { Movie } from './Movie';

@Entity('networks')
@Index(['externalSource', 'externalId'], { unique: true })
export class Network {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string

    @Column()
    externalId: string
    
    @Column()
    externalSource: ExternalSource = ExternalSource.TheMovieDb

    @ManyToMany(type => Show)
    @JoinTable()
    shows: Show[]

    @ManyToMany(type => Movie)
    @JoinTable()
    movies: Movie[]
}