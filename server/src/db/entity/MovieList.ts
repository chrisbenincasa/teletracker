import 'reflect-metadata';

import { ManyToOne, PrimaryGeneratedColumn, Entity } from 'typeorm';

import { User } from './User';

@Entity('movie_lists')
export class MovieList {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToOne(type => User, user => user.movieLists)
    user: User;
}