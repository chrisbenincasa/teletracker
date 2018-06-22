import { Column, Entity, ManyToOne, PrimaryGeneratedColumn, CreateDateColumn } from 'typeorm';

import { User } from './User';

@Entity()
export class Event {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToOne(t => User)
    user: User

    @Column()
    type: EventType

    @Column({ type: 'text', nullable: true })
    details?: string

    @CreateDateColumn()
    timestamp: Date
}

export enum EventType {
    MarkedAsWatched = 'MarkedAsWatched'
}