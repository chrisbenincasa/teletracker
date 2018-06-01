import 'reflect-metadata';
import { createConnection, Connection } from 'typeorm';
import { Config } from '../Config';

export abstract class Database {
    abstract connect(name?: string): Promise<Connection>
}

export class Db extends Database {
    private config: Config;

    constructor(config: Config) {
        super();
        this.config = config;
    }

    async connect(name?: string): Promise<Connection> {
        // This will be factored out to config at some point.
        const debug = !!process.env.DEBUG;

        const options = Object.assign({}, this.config.db, {
            logging: debug,
            name: name
        });
        
        return createConnection(options);
    }
}