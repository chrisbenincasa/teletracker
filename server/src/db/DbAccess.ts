import { Connection, Repository, FindOneOptions } from "typeorm";
import * as Entity from './entity';
import { User } from "./entity";
import * as bcrypt from 'bcrypt';
import * as R from 'ramda';
import { Optional } from '../util/Types';

export default class DbAccess {
    private connection: Connection;

    private userRepository: Repository<Entity.User>;
    private objectRepository: Repository<Entity.Thing>;
    private listRepository: Repository<Entity.List>;

    constructor(connection: Connection) {
        this.connection = connection;
        this.objectRepository = this.connection.getRepository(Entity.Thing);
        this.listRepository = this.connection.getRepository(Entity.List);
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
                    'lists': 'user.lists',
                    'shows': 'lists.things'
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
            let list = new Entity.List;
            list.user = Promise.resolve(user);
            list.isDefault = true;
            list.name = 'Default List';

            list = await this.listRepository.save(list);

            user.lists = [list];

            return user;
        });
    }

    getListForUser(userId: string | number, listId: string | number): Promise<Optional<Entity.List>> {
        // return this.getListForUser(userId, listId, 'showLists', 'shows');
        return this.listRepository.findOne(listId, { 
            join: { 
                alias: 'list',
                leftJoinAndSelect: {
                    shows: 'list.things',
                }
            } 
        }).then(list => {
            if (list) {
                return list.user.then(user => {
                    return user && user.id == userId ? list : undefined;
                });
            } else {
                return;
            }
        });
    }

    async saveObject(movie: Entity.Thing) {
        const repo = this.objectRepository;

        return repo.findOne({ where: { normalizedName: movie.normalizedName }}).then(async foundMovie => {
            if (foundMovie) {
                let newMovie = R.mergeDeepRight(foundMovie, movie)
                await repo.update(foundMovie.id, newMovie);
                return newMovie;
            } else {
                return repo.save(movie);
            }
        });
    }

    getObjectById(showId: string | number): Promise<Optional<Entity.Thing>> {
        return this.objectRepository.findOne(showId);
    }

    //
    // ShowLists
    //

    addObjectToList(show: Entity.Thing, list: Entity.List): Promise<Entity.List> {
        list.things = (list.things || []).concat([show]);
        return this.listRepository.save(list);
    }
}