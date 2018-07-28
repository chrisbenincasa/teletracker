import { Thing } from "./external/themoviedb";

export interface List {
    things: Thing[]       
}

export interface User {
    name: string,
    lists: List[]
}