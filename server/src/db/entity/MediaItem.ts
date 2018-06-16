import { Column, PrimaryGeneratedColumn } from 'typeorm';

export abstract class MediaItem {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column()
    externalSource: ExternalSource = ExternalSource.TheMovieDb

    @Column()
    externalId: string
}

export enum ExternalSource {
    TheMovieDb = 1
}