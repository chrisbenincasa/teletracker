export interface Person {
    adult?: boolean
    also_known_as?: object[]
    biography?: string
    birthday?: string
    deathday?: string
    gender?: number
    homepage?: string
    id: number
    imdb_id?: string
    name?: string
    place_of_birth?: string
    popularity?: number
    profile_path?: string
}

export interface CastMember {
    character?: string
    credit_id?: string
    gender?: number
    id: number
    name?: string
    order?: number
    profile_path?: string
}

export interface CrewMember {
    credit_id?: string
    department?: string
    gender?: number
    id: number
    job?: string
    name?: string
    profile_path?: string
}