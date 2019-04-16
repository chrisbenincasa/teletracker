import { Thing } from './external/themoviedb/Movie';

export interface List {
  id: number;
  name: string;
  things: Thing[];
}

export interface User {
  id: number;
  name: string;
  lists: List[];
}
