const withImages = require('next-images');
const fs = require('fs')
const { PHASE_PRODUCTION_BUILD } = require('next/constants');

const NODE_ENV = process.env.NODE_ENV;
if (!NODE_ENV) {
  throw new Error(
    'The NODE_ENV environment variable is required but was not specified.'
  );
}

const dotenvFiles = [
  `.env.${NODE_ENV}.local`,
  `.env.${NODE_ENV}`,
  // Don't include `.env.local` for `test` environment
  // since normally you expect tests to produce the same
  // results for everyone
  NODE_ENV !== 'test' && `,env.local`,
  '.env',
].filter(Boolean);

dotenvFiles.forEach(dotenvFile => {
  if (fs.existsSync(dotenvFile)) {
    require('dotenv-expand')(
      require('dotenv').config({
        path: dotenvFile,
      })
    );
  }
});

module.exports = (phase, { defaultConfig }) => {
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
