const manifest = require('./manifest.json');
const cloudFrontCompat = require('./next-aws-cloudfront');

const router = manifest => {
  console.time('router init');
  const {
    pages: { ssr, html },
  } = manifest;

  const allDynamicRoutes = { ...ssr.dynamic, ...html.dynamic };

  console.timeEnd('router init');
  return path => {
    console.time('route handle');
    if (ssr.nonDynamic[path]) {
      return ssr.nonDynamic[path];
    }

    for (let dynamicRoute in allDynamicRoutes) {
      const { file, regex } = allDynamicRoutes[dynamicRoute];

      const re = new RegExp(regex, 'i');
      const pathMatchesRoute = re.test(path);

      if (pathMatchesRoute) {
        console.timeEnd('route handle');
        return file;
      }
    }

    // path didn't match any route, return error page
    console.timeEnd('route handle');
    return 'pages/_error.js';
  };
};

const normaliseUri = uri => (uri === '/' ? '/index' : uri);
const route = router(manifest);

exports.handler = async event => {
  console.time('handler');
  const request = event.Records[0].cf.request;
  const uri = normaliseUri(request.uri);
  const { pages, publicFiles } = manifest;

  const isStaticPage = pages.html.nonDynamic[uri];
  const isPublicFile = publicFiles[uri];

  if (isStaticPage || isPublicFile) {
    request.origin.s3.path = isStaticPage ? '/static-pages' : '/public';

    if (isStaticPage) {
      request.uri = uri + '.html';
    }

    console.log('Handled static page');
    console.time('handler');
    return request;
  }

  const pagePath = route(uri);

  if (pagePath.endsWith('.html')) {
    request.origin.s3.path = '/static-pages';
    request.uri = pagePath.replace('pages', '');
    console.log('Handled static page after routing');
    console.timeEnd('handler');
    return request;
  }

  console.time(`require ${pagePath}`);
  const page = require(`./${pagePath}`);
  console.timeEnd(`require ${pagePath}`);

  const { req, res, responsePromise } = cloudFrontCompat(event.Records[0].cf);

  console.time('render');
  page.render(req, res);

  responsePromise.finally(() => {
    console.timeEnd('handler');
    console.timeEnd('render');
  });

  return responsePromise;
};
