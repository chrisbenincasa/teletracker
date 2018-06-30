import { Column, Entity, Index, PrimaryGeneratedColumn, JoinColumn, OneToMany, ManyToOne } from 'typeorm';

import { ExternalSource } from './Thing';
import { Network } from './Network';

@Entity('network_references')
@Index(['externalSource', 'externalId', 'network'], { unique: true })
export class NetworkReference {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToOne(type => Network, n => n.references)
    @JoinColumn({ name: 'networkId' })
    network: Network;

    @Column()
    externalSource: ExternalSource;

    @Column()
    externalId: string;
}