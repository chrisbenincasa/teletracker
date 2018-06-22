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
            }).catch(reason => {
                console.error(reason);
            });
        });

        // Retrieve a user
        this.router.get('/users/:id', AuthMiddleware.protectForSelfOrId(), async (ctx) => {
            let user = await this.dbAccess.getUserById(ctx.user.id, true);

            if (user) {
                ctx.status = 200;
                ctx.body = { data: user };
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
        this.router.post('/users/:id/lists', AuthMiddleware.protectForSelfOrId(), async ctx => {
            const user = ctx.user;

            if (!user) {
                ctx.status = 400;
                ctx.body = { error: 'Cannot create a list for a user that doesn\'t exist!' };
            } else if (!ctx.request.body) {
                ctx.status = 400;
                ctx.body = { error: 'Invalid request body or request body missing.' };
            } else {
                let req = ctx.request.body;
                let entityType: typeof Entity.List;

                if (req.type.toLowerCase() === 'show') {
                    entityType = Entity.List;
                } else if (req.type.toLowerCase() === 'movie') {
                    entityType = Entity.List;
                }

                if (!entityType) {
                    ctx.status = 400;
                    ctx.body = { error: 'Unrecognized list type = ' + req.type };
                } else {
                    let listRepo = this.dbConnection.getRepository(Entity.List);
                    let list = listRepo.create();
                    list.user = Promise.resolve(user);
                    list.name = req.name;
                    list = await listRepo.save(list);
                    ctx.status = 201;
                    ctx.body = { data: { id: list.id } };
                }
            }
        });

        // Retrieve a specific show-based list for a user
        this.router.get('/users/:id/lists/:listId', AuthMiddleware.protectForSelfOrId(), async ctx => {
            let listAndShows = await this.dbAccess.getListForUser(ctx.user.id, ctx.params.listId);

            if (!listAndShows) {
                ctx.status = 404;
            } else {
                ctx.status = 200;
                ctx.body = { data: listAndShows };
            }
        });

        // Tracks a show on the given list
        this.router.put('/users/:id/lists/:listId/tracked', AuthMiddleware.protectForSelfOrId(), async (ctx, next) => {
            if (!ctx.request.body.itemId) {
                ctx.status = 400;
                next();
            }

            let list = await this.dbAccess.getListForUser(ctx.user.id, ctx.params.listId);
            if (list) {
                let req = ctx.request.body;
                let object = await this.dbAccess.getObjectById(req.itemId);
                if (!object) {
                    ctx.status = 400;
                } else {
                    await this.dbAccess.addObjectToList(object, list);
                    ctx.status = 200;
                }
            } else {
                ctx.status = 400;
            }
        });

        //
        // Events
        //

        this.router.get('/users/:id/events', AuthMiddleware.protectForSelfOrId(), async ctx => {
            return this.dbAccess.getEventsForUser(ctx.user.id).then(events => {
                ctx.status = 200;
                ctx.body = { data: events };
            });
        });

        this.router.post('/users/:id/events', AuthMiddleware.protectForSelfOrId(), async ctx => {
            if (!ctx.request.body.event) {
                ctx.status = 400;
            } else {
                return this.dbAccess.addEventForUser(ctx.user, ctx.request.body.event).then(entity => {
                    ctx.status = 201;
                    ctx.body = { data: { id: entity.id } };
                });
            }
        });
    }
}