import { Column, Entity, Index, JoinColumn, ManyToOne, PrimaryGeneratedColumn } from 'typeorm';

import { Network } from './Network';
import { ExternalSource } from './Thing';

@Entity('network_references')
@Index(['externalSource', 'externalId', 'network'], { unique: true })
export class NetworkReference {
    @PrimaryGeneratedColumn()
    id: number;

    @ManyToOne(type => Network, n => n.references, { nullable: false })
    @JoinColumn({ name: 'networkId' })
    network: Network;

    @Column()
    externalSource: ExternalSource;

    @Column()
    externalId: string;
}