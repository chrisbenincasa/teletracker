import { Column, Entity, JoinTable, OneToMany, PrimaryGeneratedColumn, ManyToMany, ManyToOne } from 'typeorm';
import { GenreReference } from './GenreReference';
import { Thing } from './Thing';

@Entity()
export class Genre {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column()
    type: GenreType
    
    @Column()
    slug: string;

    @ManyToMany(type => Thing, t => t.genres, { eager: false })
    things: Promise<Thing[]>

    @OneToMany(type => GenreReference, g => g.genre)
    references: GenreReference[]

    @Column({ type: 'jsonb' })
    externalIds: ExternalGenreIds
}

export enum GenreProvider {
    themoviedb = 'themoviedb'
}

export type ExternalGenreIds = {
    [name in keyof typeof GenreProvider]: string
}

export enum GenreType {
    Movie = 'movie',
    Tv = 'tv'
}