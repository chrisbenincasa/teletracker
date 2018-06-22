import 'reflect-metadata';

import * as bcrypt from 'bcrypt';
import { Column, Entity, OneToMany, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm';

import { List } from './List';
import { Token } from './Token';
import { Event } from './Event';

@Entity('users')
export class User {
    constructor(name: string, username?: string, email?: string) {
        this.name = name;
        this.username = username;
        this.email = email;
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

    @OneToMany(type => Token, 'user', { eager: false })
    tokens: Promise<Token[]>

    @OneToMany(type => List, list => list.user)
    lists: List[]

    @OneToMany(type => Event, ev => ev.user)
    events: Event[]

    @CreateDateColumn()
    createdAt: Date;

    @UpdateDateColumn()
    updatedAt: Date;

    async passwordEquals(test: string) {
        return bcrypt.compare(test, this.password);
    }
}