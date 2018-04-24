import * as Koa from 'koa';
import * as Router from 'koa-router';

import { Middleware } from './middleware/Middleware';
import { Db } from './db/Connection';
import { UsersController } from './api/UsersController';
import { Connection } from 'typeorm';

class Server {
    async main(port: number = 3000): Promise<void> {
        const app = new Koa();
        const router = new Router();

        const db = await new Db().connect();

        Middleware.setupMiddleware(app);

        this.configure(router, db);

        app.use(router.routes());

        app.listen(port);

        console.log(`Server running on port ${port}`);
    }

    configure(router: Router, db: Connection): void {
        const controllers = [
            new UsersController(router, db)
        ];

        controllers.forEach(controller => controller.setupRoutes());
    }
}

new Server().main();