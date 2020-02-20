const withImages = require('next-images');
const { PHASE_PRODUCTION_BUILD } = require('next/constants');
require('dotenv').config();

module.exports = (phase, { defaultConfig }) => {
  let envConfig;
  // Only reference the .env file when we're building for prod.
  if (phase === PHASE_PRODUCTION_BUILD) {
    envConfig = { path: './.env' };
  }

  require('dotenv').config(envConfig);

  const env = Object.keys(process.env)
    .filter(key => key.startsWith('REACT_'))
    .reduce((obj, key) => {
      obj[key] = process.env[key];
      return obj;
    }, {});

  console.log(env);

  return withImages({
    env,
    target: 'serverless',
  });
};
