import 'mocha';

import * as R from 'ramda';
import { Config, GlobalConfig } from '../Config';
import { Database, Db } from '../db/Connection';
import Server from '../Server';
import PostgresDocker from './fixtures/docker';
import { getRandomPort } from './util';
import DbAccess from '../db/DbAccess';
import { Connection } from 'typeorm';
import * as uuid from 'uuid/v4';
import { User } from '../db/entity';

export default abstract class TestBase {
    port = getRandomPort();
    baseServerUrl = `http://localhost:${this.port}`;
    timeout = 30000;
    name: string;
    server: Server;
    postgresDocker: PostgresDocker;
    config: Config = R.mergeDeepRight(GlobalConfig, {
        server: { port: this.port }
    });
    connection: Connection;
    dbAccess: DbAccess;

    constructor(name: string) {
        this.name = name;
    }

    async defaultBefore(startPostgresDocker: boolean = false, startServer: boolean = false) {
        if (startPostgresDocker) {
            await this.startPostgresDocker();
            this.config = R.mergeDeepRight(this.config, {
                db: {
                    type: 'postgres',
                    host: 'localhost',
                    port: this.postgresDocker.boundPort,
                    password: 'teletracker',
                    name: this.name
                }
            })
        }
        
        if (startServer) {
            await this.startServer(this.config);
            this.connection = this.server.connection;
            this.dbAccess = new DbAccess(this.connection);
        }
    }

    async defaultAfter() {
        await this.stopServer();
        await this.stopPostgresDocker();
    }

    async startPostgresDocker() {
        this.postgresDocker = new PostgresDocker();
        await this.postgresDocker.client.ping();
        try {
            await this.postgresDocker.startDb();
            return this.postgresDocker;
        } catch (e) {
            console.error(e);
        }
    }

    async stopPostgresDocker() {
        await this.postgresDocker.stopDb();
    }

    async startServer(config?: Config, db?: Database) {
        this.server = new Server(config, db || new Db(config));
        await this.server.main().catch(console.error);
        return this.server;
    }

    async stopServer() {
        if (this.server && this.server.instance) {
            return new Promise(resolve => {
                this.server.instance.close(() => {
                    resolve()
                });
            });
        }
    }

    async generateUser(): Promise<User> {
        let user = new User('Gordon');
        user.username = uuid();
        user.email = uuid();
        user.password = '12345';

        return this.dbAccess.addUser(user);
    }

    abstract makeSpec(): Mocha.ISuite
}