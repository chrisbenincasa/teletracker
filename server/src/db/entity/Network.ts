import { Column, Entity, Index, JoinColumn, JoinTable, ManyToMany, OneToMany, PrimaryGeneratedColumn } from 'typeorm';

import { Availability } from './Availability';
import { ExternalSource, Thing } from './Thing';
import { NetworkReference } from './NetworkReference';

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

    // @Column()
    // externalId: string;
    
    // @Column()
    // externalSource: ExternalSource = ExternalSource.TheMovieDb;

    @ManyToMany(type => Thing)
    @JoinTable()
    things: Thing[];

    @OneToMany(type => Availability, a => a.network)
    @JoinTable()
    availability: Availability[]

    @OneToMany(type => NetworkReference, 'networkId')
    references: NetworkReference[]
}
