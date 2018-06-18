import { CastMember, CrewMember } from '.';
import { Genre } from './Common';

export interface TvShow {
    backdrop_path?: string
    created_by?: TvShowCreatedBy[]
    episode_run_time?: number[]
    first_air_date?: string
    genres?: Genre[]
    homepage?: string,
    id: number
    in_production?: boolean
    languages?: string[]
    last_air_date?: string
    name: string
    networks?: TvNetwork[]
    number_of_episodes?: number
    number_of_seasons?: number
    origin_country?: string[]
    original_language?: string
    original_name?: string
    overview?: string
    popularity?: number
    poster_path?: string
    seasons?: TvShowSeason[]
    status?: string
    type?: string
    vote_average?: number
    vote_count?: number
}

export interface TvNetwork {
    id: number
    name: string
}

export interface TvShowSeason {
    air_date?: string
    episode_count?: number
    episodes?: TvShowEpisode[]
    id: number
    poster_path?: string
    season_number?: number
    name?: string
    overview?: string

    // Appended joins
    credits?: TvShowCredits
}

export interface TvShowEpisode {
    air_date?: string
    crew?: object[]
    episode_number?: number
    guest_stars?: object[]
    name?: string
    overview?: string
    id: number
    production_code?: string
    season_number?: number
    still_path?: string
    vote_average?: number
    vote_count?: number
}

export interface TvShowCredits {
    cast?: CastMember[]
    crew?: CrewMember[]
}

export interface TvShowCreatedBy {
    id: number,
    name: string,
    gender?: number,
    profile_path: string
}

export interface SearchTvShowsRequest {
    query: string
    language?: string
    page?: number
    first_air_date_year?: number
}

export interface TvExternalIds {
    tvdb_id?: number
    id: number
}