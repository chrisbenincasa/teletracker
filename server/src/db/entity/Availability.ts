import { Column, Entity, JoinColumn, ManyToOne, PrimaryColumn, Index } from 'typeorm';

import { Network } from './Network';
import { Thing } from './Thing';

@Entity()
@Index((a: Availability) => [a.thing, a.network])
export class Availability { 
    @PrimaryColumn()
    id: number

    @ManyToOne(type => Thing)
    @JoinColumn()
    thing: Thing;

    @ManyToOne(type => Network)
    @JoinColumn()
    network: Network;

    @Column()
    isAvailable: boolean;

    @Column()
    region: string;

    @Column({ nullable: true })
    startDate?: Date

    @Column({ nullable: true })
    @Index()
    endDate?: Date

    @Column({ type: 'decimal', precision: 15, scale: 9 }) // Is decimal right for postgres? 
    cost?: number

    @Column()
    currency?: string
}