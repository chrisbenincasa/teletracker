import * as Koa from 'koa';
import logger from '../Logger';
const convert = require('koa-convert');
const bodyParser = require('koa-body-parser');
import { performance } from 'perf_hooks';

export class Middleware {
    static setupMiddleware(app: Koa): void {
        app.use(new ErrorHandlingMiddleware().onRequest).
            use(convert(bodyParser())).
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
        logger.debug(`Requesting url = ${ctx.url}`);
        await next();
    }
}

class TimingMiddleware extends BaseMiddleware {
    async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void> {
        const start = performance.now();
        await next();
        const end = performance.now();
        logger.debug(`Request took ${end - start} ms`);
    }
}