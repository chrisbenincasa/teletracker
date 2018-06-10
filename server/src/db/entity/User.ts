import 'reflect-metadata';

import * as bcrypt from 'bcrypt';
import { Column, Entity, OneToMany, PrimaryGeneratedColumn } from 'typeorm';

import { MovieList } from './MovieList';
import { ShowList } from './ShowList';
import { Token } from './Token';

@Entity('users')
export class User {
    constructor(name: string, username?: string, email?: string) {
        this.name = name;
    }

    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column({ unique: true })
    username: string

    @Column({ unique: true })
    email: string;

    @Column()
    password?: string;

    @OneToMany(type => Token, 'user')
    tokens: Token[]

    @OneToMany(type => MovieList, list => list.user)
    movieLists: MovieList[]

    @OneToMany(type => ShowList, list => list.user)
    showLists: ShowList[]

    async passwordEquals(test: string) {
        return bcrypt.compare(test, this.password);
    }
}