import { Thing } from "./external/themoviedb";

export interface List {
    name: string
    things: Thing[]       
}

export interface User {
    name: string,
    lists: List[]
}