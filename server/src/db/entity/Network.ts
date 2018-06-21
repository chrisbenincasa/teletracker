import { Entity, PrimaryGeneratedColumn, Column, ManyToMany, JoinTable, Index } from 'typeorm';
import { ExternalSource } from './Thing';
import { Thing } from './Thing';

@Entity('networks')
@Index(['externalSource', 'externalId'], { unique: true })
export class Network {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string

    @Column()
    externalId: string
    
    @Column()
    externalSource: ExternalSource = ExternalSource.TheMovieDb

    @ManyToMany(type => Thing)
    @JoinTable()
    things: Thing[]
}