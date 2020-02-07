const withImages = require('next-images');
const config = withImages({
  env: {
    REACT_APP_AUTH_DOMAIN: 'auth.qa.teletracker.tv',
    REACT_APP_TELETRACKER_URL: 'https://api.qa.teletracker.tv',
    REACT_APP_AUTH_REDIRECT_URI: 'https://qa.teletracker.tv/login',
    REACT_APP_AUTH_REDIRECT_SIGNOUT_URI: 'https://qa.teletracker.tv',
    REACT_APP_AUTH_REGION: 'us-west-2',
    REACT_APP_USER_POOL_ID: 'us-west-2_K6E5m6v90',
    REACT_APP_USER_POOL_CLIENT_ID: '3e5t3s3ddfci044230p3f3ki29',
  },
});

console.log(config);

module.exports = config;
