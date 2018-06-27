import { Column, Entity, Index, PrimaryGeneratedColumn } from 'typeorm';

import { ExternalSource } from './Thing';

@Entity('network_references')
@Index(['externalSource', 'externalId', 'networkId'], { unique: true })
export class NetworkReference {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    @Index()
    networkId: number;

    @Column()
    externalSource: ExternalSource;

    @Column()
    externalId: string;
}