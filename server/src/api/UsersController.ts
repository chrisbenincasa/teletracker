import * as Router from 'koa-router';
import { Connection, Repository } from 'typeorm';

import { Controller } from './Controller';
import * as Entity from '../db/entity'

export class UsersController extends Controller {
    private dbConnection: Connection;
    private userRepository: Repository<Entity.User>;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.dbConnection = connection; // Hang onto a connection to the DB
        this.userRepository = connection.manager.getRepository(Entity.User);
    }

    setupRoutes(): void {
        this.router.get('/users', async (ctx) => {
            let users = await this.userRepository.find();

            ctx.body = { data: users };
        });

        this.router.get('/users/:id', async (ctx) => {
            let user = await this.userRepository.findOne(ctx.params.id);

            if (user) {
                ctx.status = 200;
                ctx.body = { data: user };
            } else {
                ctx.status = 404;
            }
        });

        this.router.get('/users/:id/lists', async (ctx) => {
            let userWithTrackedShows = await this.userRepository.findOne({
                where: {
                    id: ctx.params.id
                },
                join: {
                    alias: 'user',
                    leftJoinAndSelect: {
                        'movieLists': 'user.movieLists',
                        'showLists': 'user.showLists'
                    }
                }
            });

            if (userWithTrackedShows) {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedShows };
            } else {
                ctx.state = 404;
            }
        });

        this.router.get('/users/:id/lists/shows/:listId', async (ctx) => {
            let userWithTrackedShowsQuery = this.userRepository.createQueryBuilder('user').
                leftJoinAndSelect('user.showLists', 'showLists').
                where('user.id = :userId', { userId: ctx.params.id }).
                andWhere('showLists.id = :listId', { listId: ctx.params.listId });

            let userWithTrackedShows = await userWithTrackedShowsQuery.getOne();
            ctx.status = 200;
            ctx.body = { data: userWithTrackedShows };
        });

        this.router.put('/users/:id/tracked/shows', async (ctx) => {
            let user = await this.userRepository.findOne(ctx.params.id);

            if (user) {
                // await this.dbConnection.getRepository(Entity.Show).insert()

                ctx.status = 200;
            } else {
                ctx.status = 404;
            }
        });
    }
}