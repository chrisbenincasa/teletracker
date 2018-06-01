import { Entity, JoinTable, ManyToMany, ManyToOne, PrimaryGeneratedColumn } from 'typeorm';

import { Show } from '.';
import { User } from './User';

@Entity('show_lists')
export class ShowList {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToMany(type => Show, show => show.showLists)
    @JoinTable()
    shows: Show[];

    @ManyToOne(type => User, user => user.showLists)
    user: User;
}