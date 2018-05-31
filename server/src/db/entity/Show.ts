import 'reflect-metadata';

import { Column, Entity, JoinTable, ManyToMany, PrimaryGeneratedColumn, Index } from 'typeorm';
import { User } from './User';
import { ShowList } from './ShowList';

@Entity('shows')
@Index(['externalSource', 'externalId'], { unique: true })
export class Show {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column()
    externalSource: ExternalSource = ExternalSource.TheMovieDb

    @Column()
    externalId: string

    @ManyToMany(type => ShowList, list => list.shows)
    showLists: ShowList[]
}

export enum ExternalSource {
    TheMovieDb = 1
}