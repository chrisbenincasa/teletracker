const withImages = require('next-images');
require('dotenv').config();

const env = Object.keys(process.env)
  .filter(key => key.startsWith('REACT_'))
  .reduce((obj, key) => {
    obj[key] = process.env[key];
    return obj;
  }, {});

const config = withImages({
  env,
  // {
  //   REACT_APP_AUTH_DOMAIN: 'auth.qa.teletracker.tv',
  //   REACT_APP_TELETRACKER_URL: 'https://api.qa.teletracker.tv',
  //   REACT_APP_AUTH_REDIRECT_URI: 'https://qa.teletracker.tv/login',
  //   REACT_APP_AUTH_REDIRECT_SIGNOUT_URI: 'https://qa.teletracker.tv',
  //   REACT_APP_AUTH_REGION: 'us-west-2',
  //   REACT_APP_USER_POOL_ID: 'us-west-2_K6E5m6v90',
  //   REACT_APP_USER_POOL_CLIENT_ID: '3e5t3s3ddfci044230p3f3ki29',
  // },
  // webpack(config, options) {
  //   config.output = config.output || {};
  //   config.output.devtoolModuleFilenameTemplate = function(info) {
  //     return 'file:///' + encodeURI(info.absoluteResourcePath);
  //   };
  //   return config;
  // },
});

module.exports = config;
