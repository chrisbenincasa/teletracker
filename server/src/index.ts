import { GlobalConfig as Config } from './Config';
import Server from "./Server";

process.on('unhandledRejection', error => {
    console.error('unhandledRejection', error);
});

new Server(Config).main();