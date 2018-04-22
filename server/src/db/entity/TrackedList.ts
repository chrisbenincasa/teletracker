import 'reflect-metadata';

import { Column, Entity, JoinTable, ManyToMany, PrimaryGeneratedColumn } from 'typeorm';
import { User } from './User';

@Entity('shows')
export class Show {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToMany(type => User, user => user.shows)
    usersWhoTrack: User[]
}