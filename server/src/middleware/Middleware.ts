import * as Koa from 'koa';
import * as passport from 'koa-passport';
import * as session from 'koa-session';
import { performance } from 'perf_hooks';
import { Connection } from 'typeorm';

import DbAccess from '../db/DbAccess';
import logger from '../Logger';
import AuthMiddleware from './AuthMiddleware';

const convert = require('koa-convert');
const bodyParser = require('koa-body-parser');
export class Middleware {
    static setupMiddleware(app: Koa, db: Connection): void {
        AuthMiddleware.setup(new DbAccess(db));

        // Session are disabled currently because the main consumer is react-native
        // which doesn't have default support for Session Cookies like a browser.
        app.use(new ErrorHandlingMiddleware().onRequest).
            // use(session({}, app)).
            use(convert(bodyParser())).
            use(passport.initialize()).
            // use(passport.session()).
            use(new LoggingMiddleware().onRequest).
            use(new TimingMiddleware().onRequest);
            // use(new TimeoutMiddleware().onRequest);
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
        logger.debug(`Requesting url = ${ctx.method} ${ctx.url}`);
        await next();
    }
}

class TimingMiddleware extends BaseMiddleware {
    async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void> {
        const start = performance.now();
        await next();
        const end = performance.now();
        logger.debug(`Request returning ${ctx.status} took ${end - start} ms`);
    }
}

// class TimeoutMiddleware extends BaseMiddleware {
//     async onRequest(ctx: Koa.Context, next: () => Promise<any>): Promise<void> {
//         let timeoutPromise = Promise.resolve(setTimeout.__promisify__(10000)).then(() => {
//             ctx.status = 503;
//         });
//         await Promise.race<void>([next(), timeoutPromise]);
//     }
// }