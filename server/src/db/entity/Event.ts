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

    @Column()
    targetEntityType: TargetEntityType

    @Column()
    targetEntityId: number

    @Column({ type: 'text', nullable: true })
    details?: string

    @CreateDateColumn({ type: 'timestamp with time zone'})
    timestamp: Date

    targetEntity?: any;
}

export enum EventType {
    MarkedAsWatched = 'MarkedAsWatched'
}

export enum TargetEntityType {
    Show = 'Show'
}