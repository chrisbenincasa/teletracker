import { Entity, Index, ManyToMany } from 'typeorm';

import { MediaItem } from './MediaItem';
import { MovieList } from './MovieList';
import { Network } from './Network';

@Entity('movies')
@Index(['externalSource', 'externalId'], { unique: true })
export class Movie extends MediaItem {
    @ManyToMany(type => MovieList, list => list.movies)
    movieLists: MovieList[]

    @ManyToMany(type => Network, list => list.movies)
    networks: Network[]
}