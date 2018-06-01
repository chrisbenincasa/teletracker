import * as Router from 'koa-router';
import { Connection, Repository } from 'typeorm';

import * as Entity from '../db/entity';
import { Controller } from './Controller';
import DbAccess from '../db/DbAccess';

export class UsersController extends Controller {
    private dbConnection: Connection;
    private userRepository: Repository<Entity.User>;

    private dbAccess: DbAccess;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.dbAccess = new DbAccess(connection);
        this.dbConnection = connection; // Hang onto a connection to the DB
        this.userRepository = connection.manager.getRepository(Entity.User);
    }

    setupRoutes(): void {
        // Retrieve all users
        this.router.get('/users', async (ctx) => {
            let users = await this.userRepository.find();

            ctx.body = { data: users };
        });

        // Retrieve a user
        this.router.get('/users/:id', async (ctx) => {
            let user = await this.dbAccess.getUserById(ctx.params.id);

            if (user) {
                ctx.status = 200;
                ctx.body = { data: user };
            } else {
                ctx.status = 404;
            }
        });

        // Retrieve all lists for a user
        this.router.get('/users/:id/lists', async (ctx) => {
            let userWithTrackedShows = await this.dbAccess.getUserById(ctx.params.id, true);

            if (userWithTrackedShows) {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedShows };
            } else {
                ctx.state = 404;
            }
        });

        // Create a list for a user
        this.router.post('/users/:id/lists', async (ctx) => {
            let user = await this.dbAccess.getUserById(ctx.params.id);

            if (!user) {
                ctx.status = 400;
                ctx.body = { error: 'Cannot create a list for a user that doesn\'t exist!' };
            } else if (!ctx.request.body) {
                ctx.status = 400;
                ctx.body = { error: 'Invalid request body or request body missing.' };
            } else {
                let req = ctx.request.body;
                if (req.type.toLowerCase() === 'show') {
                    let listRepo = this.dbConnection.getRepository(Entity.ShowList)
                    let list = listRepo.create();
                    list.user = user;
                    list = await listRepo.save(list);

                    ctx.status = 201;
                    ctx.body = { data: { id: list.id } };
                } else if (req.type.toLowerCase() === 'movie') {
                    let movieRepo = this.dbConnection.getRepository(Entity.MovieList);
                    let list = movieRepo.create();
                    list.user = user;
                    list = await movieRepo.save(list);

                    ctx.status = 201;
                    ctx.body = { data: { id: list.id } };
                } else {
                    ctx.status = 400;
                    ctx.body = { error: 'Unrecognized list type = ' + req.type };
                }
            }
        });

        // Retrieve a specific show-based list for a user
        this.router.get('/users/:id/lists/shows/:listId', async (ctx) => {
            let userWithTrackedShows = await this.dbAccess.getShowListForUser(ctx.params.id, ctx.params.listId);

            if (!userWithTrackedShows) {
                ctx.status = 404;
            } else {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedShows };
            }
        });

        // Retrieve a specific movie-based list for a user
        this.router.get('/users/:id/lists/movies/:listId', async (ctx) => {
            let userWithTrackedMovies = await this.dbAccess.getMovieListForUser(ctx.params.id, ctx.params.listId);

            if (!userWithTrackedMovies) {
                ctx.status = 404;
            } else {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedMovies };
            }
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