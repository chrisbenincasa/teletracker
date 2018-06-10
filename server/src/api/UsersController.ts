import * as Router from 'koa-router';
import * as R from 'ramda';
import { Connection } from 'typeorm';

import DbAccess from '../db/DbAccess';
import * as Entity from '../db/entity';
import AuthMiddleware from '../middleware/AuthMiddleware';
import JwtVendor from '../util/JwtVendor';
import { Controller } from './Controller';

export class UsersController extends Controller {
    private dbConnection: Connection;
    private dbAccess: DbAccess;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.dbAccess = new DbAccess(connection);
        this.dbConnection = connection; // Hang onto a connection to the DB
    }

    setupRoutes(): void {
        // Retrieve all users
        this.router.get('/users', async (ctx) => {
            let users = await this.dbAccess.getAllUsers();

            ctx.body = { data: users };
        });

        this.router.post('/users', async ctx => {
            return this.dbAccess.addUser(ctx.request.body).then(async user => {
                await ctx.login(user);
                let token = JwtVendor.vend(user.email);
                ctx.status = 201;
                ctx.body = { data: { token } };
            });
        });

        // Retrieve a user
        this.router.get('/users/:id', AuthMiddleware.protectRouteForId(), async (ctx) => {
            let user = await this.dbAccess.getUserById(ctx.params.id);

            if (user) {
                ctx.status = 200;
                ctx.body = { data: R.omit(['password'], user) };
            } else {
                ctx.status = 404;
            }
        });

        // Retrieve all lists for a user
        this.router.get('/users/:id/lists', AuthMiddleware.protectRouteForId(), async (ctx) => {
            let userWithTrackedShows = await this.dbAccess.getUserById(ctx.params.id, true);

            if (userWithTrackedShows) {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedShows };
            } else {
                ctx.state = 404;
            }
        });

        // Create a list for a user
        this.router.post('/users/:id/lists', AuthMiddleware.protectRouteForId(), async (ctx) => {
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
        this.router.get('/users/:id/lists/shows/:listId', AuthMiddleware.protectRouteForId(), async (ctx) => {
            let userWithTrackedShows = await this.dbAccess.getShowListForUser(ctx.params.id, ctx.params.listId);

            if (!userWithTrackedShows) {
                ctx.status = 404;
            } else {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedShows };
            }
        });

        // Retrieve a specific movie-based list for a user
        this.router.get('/users/:id/lists/movies/:listId', AuthMiddleware.protectRouteForId(), async (ctx) => {
            let userWithTrackedMovies = await this.dbAccess.getMovieListForUser(ctx.params.id, ctx.params.listId);

            if (!userWithTrackedMovies) {
                ctx.status = 404;
            } else {
                ctx.status = 200;
                ctx.body = { data: userWithTrackedMovies };
            }
        });

        // Tracks a show on the given list
        this.router.put('/users/:id/lists/shows/:listId/tracked', AuthMiddleware.protectRouteForId(), async (ctx) => {
            let user = await this.dbAccess.getShowListForUser(ctx.params.id, ctx.params.listId);
            if (user) {
                let req = ctx.request.body;
                let show = await this.dbAccess.getShowById(req.showId);
                if (!show) {
                    ctx.status = 400;
                } else {
                    await this.dbAccess.addShowToList(show, user.showLists[0]);
                    ctx.status = 200;
                }
            } else {
                ctx.status = 400;
            }
        });
    }
}