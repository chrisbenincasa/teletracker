import * as fs from 'fs';
import { isProduction } from './env';

export function getFilePath(path) {
  if (isProduction()) {
    return '/tmp/' + path;
  } else {
    try {
      fs.mkdirSync('out');
    } catch (e) {}

    return 'out/' + path;
  }
}
