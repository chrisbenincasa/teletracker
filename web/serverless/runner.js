const handler = require('./lambda-handler');

console.log(process.argv);

handler
  .handler(JSON.parse(process.argv[2]))
  .then(console.log)
  .catch(console.error);
