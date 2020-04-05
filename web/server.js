const createHttpsServer = require('https').createServer;
const createHttpServer = require('http').createServer;
const { parse } = require('url');
const next = require('next');
const fs = require('fs');
const Amplify = require('@aws-amplify/core').default;
const cookie = require('cookie');
const { performance } = require('perf_hooks');

const enableHttps = process.env.ENABLE_HTTPS === 'true';
const dev = process.env.NODE_ENV !== 'production';
const app = next({ dev });
const handle = app.getRequestHandler();

const httpsOptions = {
  key: fs.readFileSync('../localcerts/privkey.pem'),
  cert: fs.readFileSync('../localcerts/fullchain.pem'),
};

if (process.env.NODE_ENV !== 'production') {
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
}

const createServer = enableHttps ? createHttpsServer : createHttpServer;

if (enableHttps) {
  console.log('Starting app with HTTPS');
}

app.prepare().then(() => {
  createServer(enableHttps ? httpsOptions : {}, (req, res) => {
    const start = performance.now();

    let cookies = req.headers.cookie || '';
    let parsed = cookie.parse(cookies);
    Amplify.configure({
      Auth: {
        storage: {
          store: {},
          getItem(key) {
            return parsed[key];
          },
          setItem(_key, _value) {
            throw new Error('auth storage `setItem` not implemented');
          },
          removeItem(_key) {
            throw new Error('auth storage `removeItem` not implemented');
          },
          clear() {
            throw new Error('auth storage `clear` not implemented');
          },
        },
      },
    });

    const parsedUrl = parse(req.url, true);
    // console.log('Serving ' + req.url);
    handle(req, res, parsedUrl).finally(() => {
      console.log(`${req.url} (${res.statusCode}) - ${performance.now() - start}ms`);
    });
  }).listen(3000, err => {
    if (err) throw err;
    console.log(`> Ready on http${enableHttps ? 's' : ''}://localhost:3000`);
  });
});
