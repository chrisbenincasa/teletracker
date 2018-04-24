import 'reflect-metadata';

import { Column, Entity, JoinTable, ManyToMany, PrimaryGeneratedColumn } from 'typeorm';
import { Show } from './TrackedList';

@Entity('users')
export class User {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    // Going to need a fields (or a new table) that has information about credentials / login

    @ManyToMany(type => Show, show => show.usersWhoTrack)
    @JoinTable()
    shows: Show[]
}