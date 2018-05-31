import * as Router from 'koa-router';
import { Connection } from 'typeorm';

import { Controller } from './Controller';
import * as Entity from '../db/entity'

export class UsersController extends Controller {
    private dbConnection: Connection;

    constructor(router: Router, connection: Connection) {
        super(router);
        this.dbConnection = connection; // Hang onto a connection to the DB
    }

    setupRoutes(): void {
        this.router.get('/users', async (ctx) => {
            let users = await this.dbConnection.getRepository(Entity.User).find();

            ctx.body = { data: users };
        });

        this.router.get('/users/:id', async (ctx) => {
            let user = await this.dbConnection.getRepository(Entity.User).findOne(ctx.params.id);

            if (user) {
                ctx.status = 200;
                ctx.body = { data: user };
            } else {
                ctx.status = 404;
            }
        });

        this.router.put('/users/:id/tracked', async (ctx) => {
            let user = await this.dbConnection.getRepository(Entity.User).findOne(ctx.params.id);

            if (user) {
                // await this.dbConnection.getRepository(Entity.Show).insert()

                ctx.status = 200;
            } else {
                ctx.status = 404;
            }
        });
    }
}