import { Database } from "./Connection";
import { Connection, Repository, FindOneOptions } from "typeorm";
import * as Entity from './entity';
import { User } from "./entity";
import * as bcrypt from 'bcrypt';
import * as R from 'ramda';

type Optional<T> = T | undefined;

export default class DbAccess {
    private connection: Connection;

    private userRepository: Repository<Entity.User>;
    private showRepository: Repository<Entity.Show>;
    private showListRepository: Repository<Entity.ShowList>;
    private movieListRepository: Repository<Entity.MovieList>;

    constructor(connection: Connection) {
        this.connection = connection;
        this.showRepository = this.connection.getRepository(Entity.Show);
        this.showListRepository = this.connection.getRepository(Entity.ShowList);
        this.movieListRepository = this.connection.getRepository(Entity.MovieList);
        this.userRepository = this.connection.getRepository(Entity.User);
    }

    //
    // Users
    //

    async getAllUsers(): Promise<Entity.User[]> {
        return this.userRepository.find();
    }

    private removePassword(user: Entity.User | undefined): Entity.User | undefined {
        if (!user) {
            return;
        } else {
            return R.omit(['password'], user);
        }
    }

    async getUserById(id: string | number, includeLists: boolean = false): Promise<Entity.User | null> {
        let baseRequest: FindOneOptions<Entity.User> = {
            where: { id: id }
        };

        if (includeLists) {
            baseRequest.join = {
                alias: 'user',
                leftJoinAndSelect: {
                    'movieLists': 'user.movieLists',
                    'showLists': 'user.showLists',
                    'shows': 'showLists.shows',
                    'movies': 'movieLists.movies'
                }
            };
        }

        return this.userRepository.findOne(baseRequest).then(this.removePassword);
    }

    async getUserByEmail(email: string): Promise<Entity.User | null> {
        return this.userRepository.findOne({ where: { email: email }});
    }

    async addUser(user: User): Promise<Entity.User> {
        if (!user.password || user.password.length === 0) {
            return Promise.reject(new Error('Cannot create a user without a password'));
        }
        const salt = await bcrypt.genSalt();
        const hash = await bcrypt.hash(user.password, salt);
        user.password = hash;
        return this.userRepository.save(user).then(this.removePassword).then(async user => {
            let showList = new Entity.ShowList;
            showList.user = Promise.resolve(user);
            showList.isDefault = true;
            showList.name = 'Default Show List';

            let movieList = new Entity.MovieList;
            movieList.user = Promise.resolve(user);
            movieList.isDefault = true;
            movieList.name = 'Default Movie List';

            showList = await this.showListRepository.save(showList);
            movieList = await this.movieListRepository.save(movieList);

            user.showLists = [showList];
            user.movieLists = [movieList];

            return user;
        });
    }

    getShowListForUser(userId: string | number, listId: string | number): Promise<Optional<Entity.ShowList>> {
        // return this.getListForUser(userId, listId, 'showLists', 'shows');
        return this.showListRepository.findOne(listId, { 
            join: { 
                alias: 'showList',
                leftJoinAndSelect: {
                    shows: 'showList.shows'
                }
            } 
        }).then(showList => {
            
            if (showList) {
                return showList.user.then(user => {
                    return user && user.id == userId ? showList : undefined;
                });
            } else {
                return;
            }
        });
    }

    getMovieListForUser(userId: string | number, listId: string | number): Promise<Entity.User | null> {
        return this.getListForUser(userId, listId, 'movieLists');
    }

    getListForUser(userId: string | number, listId: string | number, listType: string, entityType?: string): Promise<Entity.User | undefined> {
        let baseQuery = this.userRepository.createQueryBuilder('user').
            leftJoinAndSelect(`user.${listType}`, 'list').
            where('user.id = :userId', { userId: userId }).
            andWhere('list.id = :listId and list.isDeleted = false', { listId: listId });

        if (entityType) {
            baseQuery = baseQuery.leftJoinAndSelect(`list.${entityType}`, entityType);
        }

        return baseQuery.getOne().then(this.removePassword);
    }

    //
    // Movies
    //

    async saveMovie(movie: Entity.Movie) {
        const movieRepo = this.connection.getRepository(Entity.Movie);

        return movieRepo.findOne({ where: { externalId: movie.externalId, externalSource: movie.externalSource }}).then(res => {
            if (res) {
                movie.id = res.id;
                return movieRepo.update(res.id, movie);
            } else {
                return movieRepo.insert(movie);
            }
        })
    }

    //
    // Shows
    //

    getShowById(showId: string | number): Promise<Optional<Entity.Show>> {
        return this.showRepository.findOne(showId);
    }

    //
    // ShowLists
    //

    addShowToList(show: Entity.Show, list: Entity.ShowList): Promise<Entity.ShowList> {
        list.shows = (list.shows || []).concat([show]);
        return this.showListRepository.save(list);
    }
}