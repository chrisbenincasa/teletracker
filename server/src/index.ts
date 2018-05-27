import Server from "./Server";

const port = parseInt(process.env.PORT) || 3000;

new Server(port).main();

// module.exports = 