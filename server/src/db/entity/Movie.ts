import { Entity, Index, ManyToMany } from 'typeorm';

import { MediaItem } from './MediaItem';
import { MovieList } from './MovieList';

@Entity('movies')
@Index(['externalSource', 'externalId'], { unique: true })
export class Movie extends MediaItem {
    @ManyToMany(type => MovieList, list => list.movies)
    movieLists: MovieList[]
}