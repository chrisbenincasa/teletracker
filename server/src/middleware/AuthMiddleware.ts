import * as passport from 'koa-passport';
import { IMiddleware, IRouterContext } from 'koa-router';
import { ExtractJwt, Strategy as JwtStrategy, StrategyOptions } from 'passport-jwt';
import { Strategy as LocalStrategy } from 'passport-local';
import { Connection } from 'typeorm';

import { GlobalConfig } from '../Config';
import { User } from '../db/entity';
import { UserRepository } from '../db/UserRepository';


export default class AuthMiddleware {
    static protectRoute(fn: (user: User, ctx: IRouterContext) => boolean): IMiddleware {
        return async function(ctx: IRouterContext, next: () => Promise<any>): Promise<any> {
            return passport.authenticate('jwt', { session: false }, async (_, user) => {
                if (user && fn(user, ctx)) {
                    ctx.user = user;
                    await next();
                } else {
                    ctx.status = 401;
                    ctx.body = { error: 'Not authorized' };
                }
            })(ctx, next);
        }
    }

    static protectRouteLoggedIn(): IMiddleware {
        return AuthMiddleware.protectRoute(() => true);
    }

    static protectRouteForId(): IMiddleware {
        return AuthMiddleware.protectRoute((user, ctx) => user.id == ctx.params.id);
    }

    static protectForSelfOrId(): IMiddleware {
        return async function(ctx: IRouterContext, next: () => Promise<any>) {
            if (ctx.params.id === 'self') {
                return AuthMiddleware.protectRouteLoggedIn()(ctx, next);
            } else {
                return AuthMiddleware.protectRouteForId()(ctx, next);
            }
        }
    }

    static setup(connection: Connection): void {
        const userRepository = connection.getCustomRepository(UserRepository)
        const findUser: (email: string, cb: (error: any, user?: any) => void, password?: string) => Promise<void> = (email, cb, password) => {
            return userRepository.getUserByEmail(email).then(async user => {
                if (user && password) {
                    let passwordMatches = await user.passwordEquals(password);
                    cb(null, !passwordMatches ? null : user);
                } else {
                    cb(null, user);
                }
            }).catch(err => {
                cb(err);
            });
        }

        passport.serializeUser<User, string>((user, done) => {
            done(null, user.id.toString());
        });

        passport.deserializeUser<User, string>(async (id, done) => {
            let user = await userRepository.getUserById(id);
            if (user) {
                done(null, user);
            } else {
                done(new Error("No user found"));
            }
        });

        passport.use(new LocalStrategy({
            usernameField: 'email'
        }, async (userEmail, password, done) => {
            findUser(userEmail, done, password);
        }));

        const opts: StrategyOptions = {
            jwtFromRequest: ExtractJwt.fromExtractors([
                ExtractJwt.fromUrlQueryParameter('token'), 
                ExtractJwt.fromAuthHeaderAsBearerToken(),
                ExtractJwt.fromBodyField('token'), 
            ]),
            secretOrKey: GlobalConfig.auth.jwt.secret,
            issuer: GlobalConfig.auth.jwt.issuer,
            audience: GlobalConfig.auth.jwt.audience,
            passReqToCallback: true
        };

        passport.use(new JwtStrategy(opts, (request: any, jwt_payload: any, done: any) => {
            findUser(jwt_payload.sub, done);
        }));
    }
}