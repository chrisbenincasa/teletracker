import 'reflect-metadata';
import { createConnection, Connection } from 'typeorm';

export class Db {
    async connect(): Promise<Connection> {
        // This will be factored out to config at some point.
        const isTest =  process.env.NODE_ENV && process.env.NODE_ENV.toLowerCase() === 'test';
        const debug = !!process.env.DEBUG;

        if (isTest) {
            return createConnection({
                type: 'sqljs',
                entities: [
                    __dirname + "/entity/*.ts"
                ],
                synchronize: true,
                logging: debug
            });
        } else {
            return createConnection({
                type: 'postgres',
                host: 'localhost',
                port: 5432,
                username: 'teletracker',
                password: 'teletracker',
                database: 'teletracker',
                entities: [
                    __dirname + "/entity/*.ts"
                ],
                synchronize: true,
                logging: debug
            });
        }
    }
}