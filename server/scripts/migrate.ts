import { createConnection } from 'typeorm';

import { GlobalConfig } from '../src/Config';

async function main() {
    const args = process.argv.slice(2);
    const connection = await createConnection(GlobalConfig.db);

    if (args.length === 0) {
        console.error('No command passed!');
    } else if (args[0] === 'run') {
        console.log('Running all migrations that haven\'t been run!');
        await connection.runMigrations();
    } else if (args[0] === 'clear') {
        await connection.undoLastMigration();
    }

    process.exit(0);
}

main();