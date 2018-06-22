import * as Dockerode from 'dockerode';
import * as R from 'ramda';
import * as dotenv from 'dotenv';
dotenv.config();


export default class PostgresDocker {
    client: Dockerode;
    boundPort: number;
    container: Dockerode.Container;
    stopped: boolean;

    constructor() {
        this.client = new Dockerode({ socketPath: '/var/run/docker.sock' });
    }

    async startDb() {
        let existingImage = await this.client.listImages().then(imageInfo => {
            return R.find(info => R.contains('postgres:latest', info.RepoTags), imageInfo);
        })

        if (!existingImage) {
            await this.client.pull('postgres:latest', {});
        }

        this.boundPort = this.getPort();

        return new Promise((resolve, reject) => {
            this.client.createContainer({
                Image: 'postgres',
                HostConfig: {
                    PortBindings: {
                        '5432/tcp': [{ HostPort: '' + this.boundPort }]
                    }
                },
                Env: [
                    'POSTGRES_USER=teletracker',
                    'POSTGRES_PASSWORD=teletracker',
                    'POSTGRES_DB=teletracker'
                ]
            }, async (err, container) => {
                if (err) reject(err);

                this.container = await container.start();

                container.logs({ follow: true, stdout: true, stderr: true }, (err, stream) => {
                    if (err) reject(err);

                    const hook = setTimeout(() => {
                        reject(new Error('timeout'));
                    }, 30000);

                    var initLineHit = false;

                    stream.on('data', (chunk: Buffer) => {
                        let s = chunk.toString();
                        if (s.includes('PostgreSQL init process complete; ready for start up')) {
                            initLineHit = true;
                        } else if (initLineHit && s.toLowerCase().includes('database system is ready to accept connections')) {
                            clearTimeout(hook);
                            resolve();
                        }
                    });
                });
            });
        });
    }

    async stopDb() {
        return new Promise((resolve, reject) => {
            if (!this.stopped && this.container) {
                this.container.stop().then((res) => {
                    this.stopped = true;
                    resolve(res);
                }).catch(reject)
            } else {
                resolve();
            }
        })
    }

    private getPort() {
        return Math.round(Math.random() * (30000 - 15000) + 15000);
    }
}