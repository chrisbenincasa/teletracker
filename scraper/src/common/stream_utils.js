import fs from 'fs';
import { getFilePath } from './tmp_files';

export const createWriteStream = fileName => {
  let path = getFilePath(fileName);

  const stream = fs.createWriteStream(path, 'utf-8');

  let flush = new Promise((resolve, reject) => {
    stream.on('close', resolve);
    stream.on('error', reject);
  });

  return [path, stream, flush];
};
