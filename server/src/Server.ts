import * as Koa from 'koa';
import * as Router from 'koa-router';

import { Middleware } from './middleware/Middleware';
import { Db, Database } from './db/Connection';
import { UsersController } from './api/UsersController';
import { Connection } from 'typeorm';
import { MoviesController } from './api/MoviesController';
import { TvShowController } from './api/TvShowController';
import { Server as ServerInstance } from 'net';
import { Config } from './Config';
import logger from './Logger';
import { AuthController } from './api/AuthController';

export default class Server {
    config: Config
    port: number
    instance: ServerInstance
    db: Database

    constructor(config: Config, database: Database = new Db(config)) {
        this.config = config;
        this.port = config.server.port;
        this.db = database;
    }

    async main(): Promise<void> {
        const app = new Koa();
        const router = new Router().prefix('/api/v1');

        const db = await this.db.connect();

        Middleware.setupMiddleware(app, db);

        this.configure(router, db);

        app.use(router.routes());

        this.instance = app.listen(this.port);

        logger.info(`Starting server with environment ${process.env.NODE_ENV}`);
        logger.info(`Server running on port ${this.port}`);
    }

    configure(router: Router, db: Connection): void {
        const controllers = [
            new UsersController(router, db),
            new AuthController(router),
            new MoviesController(router, db),
            new TvShowController(router, db)
        ];

        controllers.forEach(controller => controller.setupRoutes());

        router.stack.forEach(layer => {
            logger.debug(`Added route ${layer.methods} ${layer.path}`);
        });
    }
}