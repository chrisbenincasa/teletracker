import { Entity, PrimaryGeneratedColumn, ManyToOne, Column } from "typeorm";
import { User } from "./User";

@Entity('tokens')
export class Token {
    @PrimaryGeneratedColumn()
    id: number;

    @Column('text')
    token: string;

    @ManyToOne(type => User, user => 'tokens')
    user: User;

    @Column()
    issuedAt: number;

    @Column({ default: false })
    isRevoked: boolean;
}