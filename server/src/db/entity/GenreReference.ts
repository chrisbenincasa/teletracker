import { Column, Entity, PrimaryGeneratedColumn, ManyToOne, JoinColumn } from 'typeorm';

import { ExternalSource } from './Thing';
import { Genre } from './Genre';

@Entity()
export class GenreReference {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToOne(type => Genre, g => g.references)
    @JoinColumn({ name: 'genreId' })
    genre: Genre;

    @Column()
    externalSource: ExternalSource;

    @Column()
    externalId: string;
}