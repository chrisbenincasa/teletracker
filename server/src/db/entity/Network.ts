import { Column, Entity, Index, JoinTable, ManyToMany, OneToMany, PrimaryGeneratedColumn, JoinColumn } from 'typeorm';

import { Availability } from './Availability';
import { NetworkReference } from './NetworkReference';
import { Thing } from './Thing';

@Entity('networks')
export class Network {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column()
    @Index({ unique: true })
    slug: string;

    @Column()
    shortname: string;

    @Column({ nullable: true })
    homepage?: string;

    @Column({ nullable: true })
    origin?: string;

    @ManyToMany(type => Thing, thing => thing.networks)
    things: Thing[];

    @OneToMany(type => Availability, a => a.network)
    @JoinTable()
    availability: Availability[]

    @OneToMany(type => NetworkReference, nr => nr.network)
    references: NetworkReference[]
}
