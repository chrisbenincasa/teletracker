import * as Koa from 'koa';
let bodyParser = require('koa-body-parser');

export class Middleware {
    static setupMiddleware(app: Koa): void {
        app.use(new ErrorHandlingMiddleware().onRequest).
            use(bodyParser()).
            use(new LoggingMiddleware().onRequest).
            use(new TimingMiddleware().onRequest);
    }
}

export abstract class BaseMiddleware {
    abstract async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void>
}

class ErrorHandlingMiddleware extends BaseMiddleware {
    async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void> {
        try {
            await next();
        } catch (err) {
            ctx.status = err.status || err.code || 500;
            ctx.body = {
                success: false,
                message: err.message,
                reason: err.reason
            };
        }
    }
}

class LoggingMiddleware extends BaseMiddleware {
    async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void> {
        console.log(`requesting url = ${ctx.url}`);
        await next();
    }
}

class TimingMiddleware extends BaseMiddleware {
    async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void> {
        console.time('request');
        await next();
        console.timeEnd('request');
    }
}