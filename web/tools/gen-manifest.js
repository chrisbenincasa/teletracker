const fse = require('fs-extra');
const path = require('path');

const {
  isDynamicRoute,
  pathToRegexStr,
  expressifyDynamicRoute,
  getAllFiles,
} = require('./utils');
const getSortedRoutes = require('./sorted-routes');
const { inspect } = require('util');

const join = path.join;

async function readPublicFiles(nextConfigPath) {
  const dirExists = await fse.exists(join(nextConfigPath, 'public'));
  if (dirExists) {
    return getAllFiles(join(nextConfigPath, 'public'))
      .map(e => e.replace(nextConfigPath, ''))
      .map(e =>
        e
          .split(path.sep)
          .slice(2)
          .join('/'),
      );
  } else {
    return [];
  }
}

async function readPagesManifest(nextConfigPath) {
  const path = join(nextConfigPath, '.next/serverless/pages-manifest.json');
  const hasServerlessPageManifest = await fse.exists(path);

  if (!hasServerlessPageManifest) {
    return Promise.reject(
      "pages-manifest not found. Check if `next.config.js` target is set to 'serverless'",
    );
  }

  const pagesManifest = await fse.readJSON(path);
  const pagesManifestWithoutDynamicRoutes = Object.keys(pagesManifest).reduce(
    (acc, route) => {
      if (isDynamicRoute(route)) {
        return acc;
      }

      acc[route] = pagesManifest[route];
      return acc;
    },
    {},
  );

  const dynamicRoutedPages = Object.keys(pagesManifest).filter(isDynamicRoute);
  const sortedDynamicRoutedPages = getSortedRoutes(dynamicRoutedPages);

  const sortedPagesManifest = pagesManifestWithoutDynamicRoutes;

  sortedDynamicRoutedPages.forEach(route => {
    sortedPagesManifest[route] = pagesManifest[route];
  });

  return sortedPagesManifest;
}

async function prepareBuildManifests(nextConfigPath) {
  const pagesManifest = await readPagesManifest(nextConfigPath);

  const defaultBuildManifest = {
    pages: {
      ssr: {
        dynamic: {},
        nonDynamic: {},
      },
      html: {
        dynamic: {},
        nonDynamic: {},
      },
    },
    publicFiles: {},
    cloudFrontOrigins: {},
  };

  const apiBuildManifest = {
    apis: {
      dynamic: {},
      nonDynamic: {},
    },
  };

  const ssrPages = defaultBuildManifest.pages.ssr;
  const htmlPages = defaultBuildManifest.pages.html;
  const apiPages = apiBuildManifest.apis;

  const isHtmlPage = p => p.endsWith('.html');
  const isApiPage = p => p.startsWith('pages/api');

  Object.entries(pagesManifest).forEach(([route, pageFile]) => {
    const dynamicRoute = isDynamicRoute(route);
    const expressRoute = dynamicRoute ? expressifyDynamicRoute(route) : null;
    if (isHtmlPage(pageFile)) {
      if (dynamicRoute) {
        htmlPages.dynamic[expressRoute] = {
          file: pageFile,
          regex: pathToRegexStr(expressRoute),
        };
      } else {
        htmlPages.nonDynamic[route] = pageFile;
      }
    } else if (isApiPage(pageFile)) {
      if (dynamicRoute) {
        apiPages.dynamic[expressRoute] = {
          file: pageFile,
          regex: pathToRegexStr(expressRoute),
        };
      } else {
        apiPages.nonDynamic[route] = pageFile;
      }
    } else if (dynamicRoute) {
      ssrPages.dynamic[expressRoute] = {
        file: pageFile,
        regex: pathToRegexStr(expressRoute),
      };
    } else {
      ssrPages.nonDynamic[route] = pageFile;
    }
  });

  const publicFiles = await readPublicFiles(nextConfigPath);

  publicFiles.forEach(pf => {
    defaultBuildManifest.publicFiles['/' + pf] = pf;
  });

  return {
    defaultBuildManifest,
    apiBuildManifest,
  };
}

prepareBuildManifests('').then(x => console.log(JSON.stringify(x, null, null)));
