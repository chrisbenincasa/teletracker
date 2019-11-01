import * as fs from 'fs'

export function getFilePath(path) {
  if (process.env.NODE_ENV === 'production') {
    return '/tmp/' + path;
  } else {
    try {
      fs.mkdirSync('out');
    } catch (e) {

    }

    return 'out/' + path;
  }
}
