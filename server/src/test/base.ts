import 'mocha';

import * as R from 'ramda';
import { Config, GlobalConfig } from '../Config';
import { Database, Db } from '../db/Connection';
import Server from '../Server';
import PostgresDocker from './fixtures/docker';
import { getRandomPort } from './util';
import { Connection } from 'typeorm';
import * as uuid from 'uuid/v4';
import { User } from '../db/entity';
import { UserRepository } from '../db/UserRepository';
import { ListRepository } from '../db/ListRepository';
import { ThingRepository } from '../db/ThingRepository';
import { EventsRepository } from '../db/EventsRepository';

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

    protected userRepository: UserRepository;
    protected listRepository: ListRepository;
    protected thingRepository: ThingRepository;
    protected eventsRepository: EventsRepository;

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
            this.userRepository = this.connection.getCustomRepository(UserRepository);
            this.listRepository = this.connection.getCustomRepository(ListRepository);
            this.thingRepository = this.connection.getCustomRepository(ThingRepository);
            this.eventsRepository = this.connection.getCustomRepository(EventsRepository);
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

        return this.userRepository.addUser(user);
    }

    abstract makeSpec(): Mocha.ISuite
}