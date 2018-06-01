import * as Koa from 'koa';
import * as Router from 'koa-router';

import { Middleware } from './middleware/Middleware';
import { Db, Database } from './db/Connection';
import { UsersController } from './api/UsersController';
import { Connection } from 'typeorm';
import { MoviesController } from './api/MoviesController';
import { TvShowController } from './api/TvShowController';
import { Server as ServerInstance } from 'net';

export default class Server {
    port: number
    instance: ServerInstance
    db: Database

    constructor(port: number, database: Database = new Db()) {
        this.port = port;
        this.db = database;
    }

    async main(): Promise<void> {
        const app = new Koa();
        const router = new Router().prefix('/api/v1');

        const db = await this.db.connect();

        Middleware.setupMiddleware(app);

        this.configure(router, db);

        app.use(router.routes());

        this.instance = app.listen(this.port);

        if (process.env.NODE_ENV.toLowerCase() !== 'test') {
          console.log(`Starting server with environment ${process.env.NODE_ENV}`);
          console.log(`Server running on port ${this.port}`);
        }
    }

    configure(router: Router, db: Connection): void {
        const controllers = [
            new UsersController(router, db),
            new MoviesController(router, db),
            new TvShowController(router, db)
        ];

        controllers.forEach(controller => controller.setupRoutes());

        router.stack.forEach(layer => {
          if (process.env.NODE_ENV.toLowerCase() !== 'test') {  
            console.log(`Added route ${layer.path}`);
          }
        });
    }
}