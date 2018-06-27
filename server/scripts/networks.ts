import fs = require('fs');
import { promisify } from 'util';

async function main() {
    let x = await promisify(fs.readFile)(process.cwd() + '/data/tv_network_ids_06_26_2018.txt');

    console.log(x.toString().split('\n').slice(0, 10));

    let y = await promisify(fs.readFile)(process.cwd() + '/data/providers.json');

    console.log(JSON.parse(y.toString()));

    process.exit(0);
}

main();