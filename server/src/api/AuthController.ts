import * as Router from 'koa-router';
import * as passport from 'passport';

import AuthMiddleware from '../middleware/AuthMiddleware';
import JwtVendor from '../util/JwtVendor';
import { Controller } from './Controller';

export class AuthController extends Controller {
    constructor(router: Router) {
        super(router);
    }

    setupRoutes(): void {
        this.router.post('/auth/login', async ctx => {
            return passport.authenticate('local', { session: false }, async (_, user) => {
                if (user) {
                    await ctx.login(user);
                    let token = JwtVendor.vend(user.email);
                    ctx.status = 200;
                    ctx.body = { data: { token } };
                } else {
                    ctx.status = 400;
                    ctx.body = { error: 'Bad' };
                }
            })(ctx);
        });

        // A route that can only be accessed while authenticated
        this.router.get('/auth/status', AuthMiddleware.protectRouteLoggedIn(), async ctx => {
            ctx.body = { data: { authenticated: true, email: ctx.user.email } };
        });

        // TODO: This doesn't really work right now... it needs to be revoking tokens? 
        this.router.post('/auth/logout', async ctx => {
            if (ctx.isAuthenticated()) {
                await ctx.logout();
                ctx.status = 200;
                ctx.body = { data: { loggedOut: true } };
            } else {
                ctx.body = { error: 'Bad' };
                ctx.body = 400;
            }
        });
    }
}