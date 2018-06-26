import { Column, Entity, Index, ManyToOne, PrimaryGeneratedColumn } from 'typeorm';

import { Network } from './Network';
import { Thing } from './Thing';
import { TvShowEpisode } from './TvShowEpisode';

@Entity()
@Index((a: Availability) => [a.thing, a.network])
export class Availability { 
    @PrimaryGeneratedColumn()
    id: number

    @ManyToOne(type => Thing, { nullable: true })
    thing: Thing;

    @ManyToOne(type => TvShowEpisode, { nullable: true })
    tvShowEpisode: TvShowEpisode

    @ManyToOne(type => Network)
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

    @Column({ nullable: true })
    offerType?: OfferType

    @Column({ type: 'decimal', precision: 15, scale: 9, nullable: true }) // Is decimal right for postgres? 
    cost?: number

    @Column({ nullable: true })
    currency?: string
}

export enum OfferType {
    Buy = 'buy',
    Rent = 'rent',
    Theater = 'theater',
    Subscription = 'subscription',
    Free = 'free',
    Ads = 'ads'
}