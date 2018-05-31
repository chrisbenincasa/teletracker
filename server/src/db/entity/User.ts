import 'reflect-metadata';

import { Column, Entity, JoinTable, ManyToMany, PrimaryGeneratedColumn, OneToMany } from 'typeorm';
import { Show } from './Show';
import { MovieList } from './MovieList';
import { ShowList } from './ShowList';

@Entity('users')
export class User {
    constructor(name: string) {
        this.name = name;
    }

    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    // Going to need a fields (or a new table) that has information about credentials / login

    @OneToMany(type => MovieList, list => list.user)
    movieLists: MovieList[]

    @OneToMany(type => ShowList, list => list.user)
    showLists: ShowList[]
}