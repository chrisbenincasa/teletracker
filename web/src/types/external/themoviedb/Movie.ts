import { Movie, Person, TvShow } from 'themoviedb-client-typed';

export interface ObjectMetadata {
  themoviedb: TheMovieDbMetadata;
}

export interface TheMovieDbMetadata {
  movie?: Movie;
  show?: TvShow;
  person?: Person;
}

export type KeyMap<T> = { [K in keyof T]?: true | KeyMap<Partial<T[K]>> };
