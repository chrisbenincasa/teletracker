import 'reflect-metadata';
import { createConnection, Connection } from 'typeorm';

export abstract class Database {
    abstract connect(name?: string): Promise<Connection>
}

export class Db extends Database {
    async connect(name?: string): Promise<Connection> {
        // This will be factored out to config at some point.
        const debug = !!process.env.DEBUG;
        
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