import { Column, PrimaryGeneratedColumn, Entity, ManyToMany, Index, OneToMany, CreateDateColumn, UpdateDateColumn } from 'typeorm';
import { Movie, TvShow, Person } from 'themoviedb-client-typed'
import { List } from './List';
import { Network } from './Network';
import { Optional } from '../../util/Types';
import { Availability } from './Availability';
import { TvShowSeason } from './TvShowSeason';
import { slugify } from '../../util/Slug';
import { Genre } from './Genre';

export enum ExternalSource {
    TheMovieDb = 'themoviedb',
    JustWatch = 'justwatch'
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
            m.normalizedName = slugify(movie.title); // What happens if this is empty? 
            m.metadata = { [ExternalSource.TheMovieDb]: { movie } };
            return m;
        }
    }

    static show(show: TvShow): Optional<Thing> {
        if (show.name) {
            let m = new Thing;
            m.type = ThingType.Show;
            m.name = show.name;
            m.normalizedName = slugify(show.name);
            m.metadata = { [ExternalSource.TheMovieDb]: { show } };
            return m;
        }
    }

    static person(person: Person): Optional<Thing> {
        if (person.name) {
            let m = new Thing;
            m.type = ThingType.Person;
            m.name = person.name;
            m.normalizedName = slugify(person.name);
            m.metadata = { [ExternalSource.TheMovieDb]: { person }};
            return m;
        }
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

    @CreateDateColumn()
    createdAt: Date;

    @UpdateDateColumn()
    lastUpdatedAt: Date;

    @ManyToMany(type => List, list => list.things)
    lists: List[];

    // Only applicable to TV shows
    @OneToMany(type => TvShowSeason, season => season.show)
    seasons: TvShowSeason[]

    @ManyToMany(type => Network, network => network.things)
    networks: Network[];

    @OneToMany(type => Availability, a => a.thing)
    availability: Availability[];

    @OneToMany(type => Genre, g => g.id)
    genres: Genre[]

    @Column({ type: 'jsonb', nullable: true })
    metadata?: ObjectMetadata;
}