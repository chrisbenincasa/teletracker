import * as Koa from 'koa';
import * as Router from 'koa-router';

import { Middleware } from './middleware/Middleware';

class Server {
    async main(port: number = 3000): Promise<void> {
        const app = new Koa();
        const router = new Router();

        Middleware.setupMiddleware(app);

        this.configure(router);

        app.use(router.routes());

        app.listen(port);

        console.log(`Server running on port ${port}`);
    }

    configure(router: Router): void {
        const controllers = [];

        // controllers.forEach(controller => controller.setupRoutes());
    }
}

new Server().main();