export interface ErrorResponse {
    status_message?: string
    status_code?: number
}

export interface Genre {
    id: number
    name: string
}

export interface ProductionCompany {
    name?: string
    id: number
    logo_path?: string
    origin_country?: string
}