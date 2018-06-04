import { ConnectionOptions } from 'typeorm';
import * as dotenv from 'dotenv';
dotenv.config();
const config = require('config');

export class ConfigLoader {
    static load(): Config {
        return {
            server: {
                port: config.get('server.port')  
            },
            logging: {
                level: (!process.env.NODE_ENV || process.env.NODE_ENV.toLowerCase() !== 'test') ? 'debug' : 'error'
            },
            db: {
                type: config.get('db.type'),
                host: config.get('db.host'),
                port: config.get('db.port'),
                username: config.get('db.username'),
                password: config.has('db.password') ? config.get('db.password') : null,
                database: config.get('db.database'),
                synchronize: config.get('db.synchronize'),
                logging: config.get('db.logging'),
                entities: config.get('db.entities'),
                migrations: config.get('db.migrations'),
                subscribers: config.get('db.subscribers')
            }
        };
    }
}

export const GlobalConfig = ConfigLoader.load();

export interface Config {
    server: ServerConfig;
    logging: LoggingConfig;
    db: ConnectionOptions;
}

export interface LoggingConfig {
    level: string
}

export interface ServerConfig {
    port: number
}