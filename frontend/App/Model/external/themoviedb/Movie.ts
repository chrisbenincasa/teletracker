import { Movie, Person, TvShow } from 'themoviedb-client-typed';

export interface SearchMoviesRequest {
    query: string
    language?: string
    page?: number
    include_adult?: boolean
    region?: string
    year?: number
    primary_release_year?: number
}

export class ObjectMetadata {
    themoviedb: TheMovieDbMetadata
}

export interface TheMovieDbMetadata {
    movie?: Movie,
    show?: TvShow,
    person?: Person
}

export interface Thing {
    id: string | number,
    name: string
    normalizedName: string
    type: 'movie' | 'show' | 'person',
    metadata?: ObjectMetadata,
    userMetadata?: ObjectMetadata,
    availability: any
}

export interface MovieExternalIds {
    imdb_id?: string
    id: number
}