import { Entity, Column, Index, PrimaryGeneratedColumn } from "typeorm";

@Entity('certifications')
@Index(['iso_3166_1', 'type', 'certification', 'order'], { unique: true })
export class Certification {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    type: CertificationType

    @Column()
    iso_3166_1: string;

    @Column()
    certification: string;

    @Column()
    description: string;

    @Column()
    order: number
}

export enum CertificationType {
    Movie = 'movie',
    Tv  = 'tv'
}