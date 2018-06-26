import { PrimaryGeneratedColumn, Entity, Column } from "typeorm";
import { ExternalSource } from "./Thing";

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

    @Column({ type: 'jsonb' })
    externalIds: ExternalGenreIds
}

export class GenreMapping {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    genreId: number;

    @Column()
    externalSource: ExternalSource;
    
    @Column()
    externalId: string;
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