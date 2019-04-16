export interface SearchMultiRequest {
    query: string
    language?: string
    page?: number, 
    include_adult?: boolean
}