import { Movie, Person, TvShow } from 'themoviedb-client-typed';
import { Availability } from '../..';

export interface SearchMoviesRequest {
  query: string;
  language?: string;
  page?: number;
  include_adult?: boolean;
  region?: string;
  year?: number;
  primary_release_year?: number;
}

export interface ObjectMetadata {
  themoviedb: TheMovieDbMetadata;
}

export interface TheMovieDbMetadata {
  movie?: Movie;
  show?: TvShow;
  person?: Person;
}

export type KeyMap<T> = { [K in keyof T]?: true | KeyMap<Partial<T[K]>> };

export interface MovieExternalIds {
  imdb_id?: string;
  id: number;
}
