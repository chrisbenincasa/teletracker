import { createConnection } from 'typeorm';

import { GlobalConfig } from '../src/Config';

async function main() {
    const connection = await createConnection(GlobalConfig.db);

    await connection.synchronize();

    process.exit(0);
}

main();