import 'reflect-metadata';
import { createConnection, Connection } from 'typeorm';

export class Db {
    async connect(): Promise<Connection> {
        // Eventually these will be loaded by config. 
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
            logging: true
        });
    }
}