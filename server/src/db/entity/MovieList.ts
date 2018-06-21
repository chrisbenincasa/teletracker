// import 'reflect-metadata';

// import { Entity, JoinTable, ManyToMany, ManyToOne } from 'typeorm';

// import { List } from './List';
// import { Movie } from './Movie';
// import { User } from './User';

// @Entity('movie_lists')
// export class MovieList extends List {
//     @ManyToMany(type => Movie, movie => movie.movieLists)
//     @JoinTable()
//     movies: Movie[];

//     @ManyToOne(type => User, user => user.movieLists)
//     user: Promise<User>;
// }