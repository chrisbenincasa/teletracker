const TerserPlugin = require ('terser-webpack-plugin');
const withImages = require('next-images');
const fs = require('fs');
const { PHASE_PRODUCTION_BUILD } = require('next/constants');

const NODE_ENV = process.env.NODE_ENV;
if (!NODE_ENV) {
  throw new Error(
    'The NODE_ENV environment variable is required but was not specified.',
  );
}

console.log('NODE_ENV=' + NODE_ENV);

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
  console.log('phase = ' + phase)

  if (phase === PHASE_PRODUCTION_BUILD && !process.env.PROD_LOCAL && !process.env.BUILD_ID) {
    throw new Error("Production builds require a BUILD_ID")
  }

  const env = Object.keys(process.env)
    .filter(key => key.startsWith('REACT_'))
    .reduce((obj, key) => {
      obj[key] = process.env[key];
      return obj;
    }, {});

  console.log('Will inject the following environment variables', env);

  let assetPrefix;
  if (phase === PHASE_PRODUCTION_BUILD) {
    if (!!process.env.PROD_LOCAL) {
      assetPrefix = ''
    } else {
      assetPrefix = process.env.BUILD_ID;
    }
  } else {
    assetPrefix = ''
  }

  console.log('Generating with assetPrefix = ' + assetPrefix);

  return withImages({
    env,
    webpack: (config, { dev, isServer }) => {
      if (!dev && !isServer) {
        config.optimization.minimizer = [new TerserPlugin({
          parallel: true,
          sourceMap: false
        })]
      }

      if (dev) {
        config.resolve = {
          ...config.resolve,
          alias: {
            ...config.resolve.alias,
            'react-redux': 'react-redux/lib'
          }
        }
      }

      config.output = {
        ...config.output,
        // path: `${config.output.path}/${process.env.BUILD_ID}`
        // publicPath: `${process.env.BUILD_ID}/`
      }

      return config
    },
    target: 'serverless',
    poweredByHeader: false,
    assetPrefix,
    generateBuildId: () => {
      return process.env.BUILD_ID;
    },
  });
};
