import { Column, PrimaryGeneratedColumn, Entity, ManyToMany, Index, OneToMany } from 'typeorm';
import { Movie, TvShow, Person } from 'themoviedb-client-typed'
import { List } from './List';
import { Network } from './Network';
import { Optional } from '../../util/Types';
import { Availability } from './Availability';

export enum ExternalSource {
    TheMovieDb = 'themoviedb'
}

export class ObjectMetadata {
    [ExternalSource.TheMovieDb]: TheMovieDbMetadata
}

export interface TheMovieDbMetadata {
    movie?: Movie,
    show?: TvShow,
    person?: Person
}

export enum ThingType {
    Movie = 'movie',
    Show = 'show',
    Person = 'person'
}

export class ThingFactory {
    static movie(movie: Movie): Optional<Thing> {
        if (movie.title) {
            let m = new Thing;
            m.type = ThingType.Movie;
            m.name = movie.title; // What happens if this is empty?
            m.normalizedName = ThingFactory.slugify(movie.title); // What happens if this is empty? 
            m.metadata = { 'themoviedb': { movie } };
            return m;
        }
    }

    static show(show: TvShow): Optional<Thing> {
        if (show.name) {
            let m = new Thing;
            m.type = ThingType.Show;
            m.name = show.name;
            m.normalizedName = ThingFactory.slugify(show.name);
            m.metadata = { 'themoviedb': { show } };
            return m;
        }
    }

    static person(person: Person): Optional<Thing> {
        if (person.name) {
            let m = new Thing;
            m.type = ThingType.Person;
            m.name = person.name;
            m.normalizedName = ThingFactory.slugify(person.name);
            m.metadata = { 'themoviedb': { person }};
            return m;
        }
    }

    static slugify(text: string) {
        return text.toString().toLowerCase().
            replace(/\s+/g, '-').
            replace(/[^\w\-]+/g, '').
            replace(/\-\-+/g, '-').
            replace(/^-+/, '').
            replace(/-+$/, '');
    }
}

@Entity("objects")
export class Thing {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Index('slug_uniq_idx', { unique: true })
    @Column()
    normalizedName: string;

    @Column()
    type: ThingType;

    @ManyToMany(type => List, list => list.things)
    lists: List[]

    @ManyToMany(type => Network, network => network.things)
    networks: Network[]

    @OneToMany(type => Availability, a => a.thing)
    availability: Availability[]

    @Column({ type: 'jsonb', nullable: true })
    metadata?: ObjectMetadata;
}