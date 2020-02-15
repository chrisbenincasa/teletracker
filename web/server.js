const { createServer } = require('https');
const { parse } = require('url');
const next = require('next');
const fs = require('fs');
const Amplify = require('@aws-amplify/core').default;
const cookie = require('cookie');

const dev = process.env.NODE_ENV !== 'production';
const app = next({ dev });
const handle = app.getRequestHandler();

const httpsOptions = {
  key: fs.readFileSync('./certificates/localhost.key'),
  cert: fs.readFileSync('./certificates/localhost.crt'),
};

app.prepare().then(() => {
  createServer(httpsOptions, (req, res) => {
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
    handle(req, res, parsedUrl);
  }).listen(3000, err => {
    if (err) throw err;
    console.log('> Ready on https://localhost:3000');
  });
});
