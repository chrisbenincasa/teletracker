// import { Entity, JoinTable, ManyToMany, ManyToOne, PrimaryGeneratedColumn, Column, CreateDateColumn, UpdateDateColumn } from 'typeorm';

// import { Show } from '.';
// import { User } from './User';
// import { List } from './List';

// @Entity('show_lists')
// export class ShowList extends List {
//     @ManyToMany(type => Show, show => show.showLists)
//     @JoinTable()
//     shows: Show[];

//     @ManyToOne(type => User, user => user.showLists, { eager: false })
//     user: Promise<User>;
// }