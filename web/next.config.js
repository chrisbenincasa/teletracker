const withImages = require('next-images');
const fs = require('fs');
const { PHASE_PRODUCTION_BUILD } = require('next/constants');
const util = require('util');
const exec = util.promisify(require('child_process').exec);

const NODE_ENV = process.env.NODE_ENV;
if (!NODE_ENV) {
  throw new Error(
    'The NODE_ENV environment variable is required but was not specified.',
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
      }),
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

  console.log('Will inject the following environment variables', env);

  return withImages({
    env,
    target: 'serverless',
    poweredByHeader: false,
    // assetPrefix: process.env.VERSION,
    generateBuildId: async () => {
      const secondsSinceEpoch = Math.round(new Date().getTime() / 1000);
      const { stdout } = await exec('git rev-parse --short HEAD');
      const buildId = `${secondsSinceEpoch}.${stdout.trim()}`;

      console.log(`Generating build with ID = ${buildId}`);

      return buildId;
    },
  });
};