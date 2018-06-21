import { Column, CreateDateColumn, Entity, JoinTable, ManyToOne, PrimaryGeneratedColumn, UpdateDateColumn, ManyToMany } from 'typeorm';

import { Thing } from './Thing';
import { User } from './User';

@Entity('lists')
export class List {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column({ default: false })
    isDefault: boolean;

    @CreateDateColumn()
    createdAt: Date

    @UpdateDateColumn()
    updatedAt: Date

    @Column({ default: false })
    isDeleted: boolean

    @ManyToOne(type => User, user => user.lists, { eager: false })
    user: Promise<User>;

    @ManyToMany(type => Thing, thing => thing.lists)
    @JoinTable()
    things: Thing[]
}