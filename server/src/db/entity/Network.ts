import { Entity, PrimaryGeneratedColumn, Column, ManyToMany, JoinTable, Index, OneToMany } from 'typeorm';
import { ExternalSource } from './Thing';
import { Thing } from './Thing';
import { Availability } from './Availability';

@Entity('networks')
@Index(['externalSource', 'externalId'], { unique: true })
export class Network {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column()
    homepage: string;

    @Column()
    origin: string;

    @Column()
    externalId: string;
    
    @Column()
    externalSource: ExternalSource = ExternalSource.TheMovieDb;

    @ManyToMany(type => Thing)
    @JoinTable()
    things: Thing[];

    @OneToMany(type => Availability, a => a.network)
    @JoinTable()
    availability: Availability[]
}