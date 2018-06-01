import { Database } from "./Connection";
import { Connection, Repository } from "typeorm";
import * as Entity from './entity';

export default class DbAccess {
    private connection: Connection;

    private userRepository: Repository<Entity.User>;
    private showRepository: Repository<Entity.Show>;
    private showListRepository: Repository<Entity.ShowList>;

    constructor(connection: Connection) {
        this.connection = connection;
        this.showRepository = this.connection.getRepository(Entity.Show);
        this.showListRepository = this.connection.getRepository(Entity.ShowList);
        this.userRepository = this.connection.getRepository(Entity.User);
    }

    //
    // Users
    //

    getAllUsers(): Promise<Entity.User[]> {
        return this.userRepository.find();
    }

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
        return this.getListForUser(userId, listId, 'showLists', 'shows');
    }

    getMovieListForUser(userId: string | number, listId: string | number): Promise<Entity.User | null> {
        return this.getListForUser(userId, listId, 'movieLists');
    }

    getListForUser(userId: string | number, listId: string | number, listType: string, entityType?: string): Promise<Entity.User | null> {
        let baseQuery = this.userRepository.createQueryBuilder('user').
            leftJoinAndSelect(`user.${listType}`, 'list').
            where('user.id = :userId', { userId: userId }).
            andWhere('list.id = :listId', { listId: listId });

        if (entityType) {
            baseQuery = baseQuery.leftJoinAndSelect(`list.${entityType}`, entityType);
        }

        return baseQuery.getOne();
    }

    //
    // Shows
    //

    getShowById(showId: string | number): Promise<Entity.Show | null | undefined> {
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