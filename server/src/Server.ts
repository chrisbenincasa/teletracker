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
import { SearchController } from './api/SearchController';
import { NetworksController } from './api/NetworksController';

export default class Server {
    config: Config
    port: number
    instance: ServerInstance
    db: Database
    connection: Connection;

    constructor(config: Config, database: Database = new Db(config)) {
        this.config = config;
        this.port = config.server.port;
        this.db = database;
    }

    async main(): Promise<void> {
        const app = new Koa();
        const router = new Router().prefix('/api/v1');

        this.connection = await this.db.connect(this.config.db.name);

        Middleware.setupMiddleware(app, this.connection);

        this.configure(router);

        app.use(router.routes());

        this.instance = app.listen(this.port);

        logger.info(`Starting server with environment ${process.env.NODE_ENV}`);
        logger.info(`Server running on port ${this.port}`);
    }

    configure(router: Router): void {
        const controllers = [
            new UsersController(router, this.connection),
            new AuthController(router),
            new MoviesController(router, this.connection),
            new TvShowController(router, this.connection),
            new SearchController(router, this.connection),
            new NetworksController(router, this.connection)
        ];

        controllers.forEach(controller => controller.setupRoutes());

        router.stack.forEach(layer => {
            logger.debug(`Added route ${layer.methods} ${layer.path}`);
        });
    }
}