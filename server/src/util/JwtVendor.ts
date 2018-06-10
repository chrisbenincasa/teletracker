import * as jwt from 'jsonwebtoken';
import * as uuid from 'uuid/v4';

import { GlobalConfig } from '../Config';

export default class JwtVendor {
    static vend(userEmail: string, expiry?: number): string {
        let payload: any = {
            iss: GlobalConfig.auth.jwt.issuer,
            aud: GlobalConfig.auth.jwt.audience,
            sub: userEmail,
            jti: this.generateJti(),
            alg: 'HS256'
        };

        if (expiry) {
            payload.exp = expiry;
        }

        let options: jwt.SignOptions = {};

        if (!expiry) {
            options.expiresIn = GlobalConfig.auth.jwt.expiration || Math.floor(Date.now() / 1000) + (60 * 60);
        }

        return jwt.sign(payload, GlobalConfig.auth.jwt.secret, options);
    } 

    static generateJti(): string {
        return uuid();
    }
}