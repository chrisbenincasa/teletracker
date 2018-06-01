import 'reflect-metadata';
import { createConnection, Connection, ConnectionOptions, getConnection } from 'typeorm';
import * as path from 'path';

const isTest = process.env.NODE_ENV && process.env.NODE_ENV.toLowerCase() === 'test';
const debug = !!process.env.DEBUG;

export class InMemoryDb {
    private defaultConnectionName: string;

    constructor(defaultConnectioName?: string) {
        this.defaultConnectionName;
    }

    static ConnectionConfig: ConnectionOptions = {
        type: 'sqljs',
        entities: [
            path.resolve(__dirname + "../../../db/entity/*.ts")
        ],
        synchronize: true,
        logging: debug
    }

    async connect(name?: string): Promise<Connection> {
        let connectionName = name || this.defaultConnectionName;
        if (isTest) {
            try {
                return Promise.resolve(getConnection(connectionName));
            } catch (e) {
                let conf = Object.assign(InMemoryDb.ConnectionConfig, { name: connectionName });
                return createConnection(conf);
            }
        } else {
            throw new Error('Attempting to run InMemoryDb in non-test environment!');
        }
    }
}