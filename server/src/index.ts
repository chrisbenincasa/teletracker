import { GlobalConfig as Config } from './Config';
import Server from "./Server";

const port = parseInt(process.env.PORT) || 3000;

new Server(Config).main();