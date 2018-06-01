import { Database } from "./Connection";
import { Connection, Repository } from "typeorm";
import * as Entity from './entity';

export default class DbAccess {
    private connection: Connection;

    private userRepository: Repository<Entity.User>

    constructor(connection: Connection) {
        this.connection = connection;
        this.userRepository = this.connection.getRepository(Entity.User);
    }

    // Users
    getUserById(id: string | number, includeLists: boolean = false): Promise<Entity.User | null> {
        let baseRequest: any = {
            where: { id: id }
        };

        if (includeLists) {
            baseRequest.join = {
                alias: 'user',
                leftJoinAndSelect: {
                    'movieLists': 'user.movieLists',
                    'showLists': 'user.showLists'
                }
            };
        }

        return this.userRepository.findOne(baseRequest);
    }

    getShowListForUser(userId: string | number, listId: string | number): Promise<Entity.User | null> {
        return this.getListForUser(userId, listId, 'showLists');
    }

    getMovieListForUser(userId: string | number, listId: string | number): Promise<Entity.User | null> {
        return this.getListForUser(userId, listId, 'movieLists');
    }

    getListForUser(userId: string | number, listId: string | number, listType: string): Promise<Entity.User | null> {
        let userWithListQuery = this.userRepository.createQueryBuilder('user').
            leftJoinAndSelect(`user.${listType}`, 'list').
            where('user.id = :userId', { userId: userId }).
            andWhere('list.id = :listId', { listId: listId });

        return userWithListQuery.getOne();
    }
}