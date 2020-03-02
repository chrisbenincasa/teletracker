const { pathToRegexp } = require('path-to-regexp');
const fs = require('fs');
const path = require('path');

const isDynamicRoute = route => {
  // Identify /[param]/ in route string
  return /\/\[[^\/]+?\](?=\/|$)/.test(route);
};

const pathToRegexStr = path =>
  pathToRegexp(path)
    .toString()
    .replace(/\/(.*)\/\i/, '$1');

const expressifyDynamicRoute = dynamicRoute => {
  // replace any catch all group first
  let expressified = dynamicRoute.replace(/\[\.\.\.(.*)]$/, ':$1*');

  // now replace other dynamic route groups
  return expressified.replace(/\[(.*?)]/g, ':$1');
};

function getAllFiles(dirPath, arrayOfFiles) {
  const files = fs.readdirSync(dirPath);

  arrayOfFiles = arrayOfFiles || [];

  files.forEach(function(file) {
    if (fs.statSync(dirPath + path.sep + file).isDirectory()) {
      arrayOfFiles = getAllFiles(path.join(dirPath, file), arrayOfFiles);
    } else {
      if (file !== '.DS_Store') {
        arrayOfFiles.push(path.join(dirPath, file));
      }
    }
  });

  return arrayOfFiles;
}

module.exports = {
  isDynamicRoute,
  pathToRegexStr,
  expressifyDynamicRoute,
  getAllFiles,
};
