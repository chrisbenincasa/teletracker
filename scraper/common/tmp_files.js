export function getFilePath(path) {
  if (process.env.NODE_ENV === 'production') {
    return '/tmp/' + path;
  } else {
    return path;
  }
}
