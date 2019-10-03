import { Storage } from '@google-cloud/storage';
import { promises as fs } from 'fs';

const storage = new Storage();
const bucket = storage.bucket('teletracker');

const writeResultsAndUploadToStorage = async (
  fileName,
  destinationDir,
  results,
) => {
  await fs.writeFile(`/tmp/${fileName}`, JSON.stringify(results), 'utf8');

  return uploadToStorage(fileName, destinationDir);
};

const uploadToStorage = async (fileName, destinationDir) => {
  let sanitized = fileName.replace(/^\/tmp\//gi, '');
  return bucket.upload(`/tmp/${sanitized}`, {
    gzip: true,
    contentType: 'application/json',
    destination: destinationDir + '/' + sanitized,
    resumable: false,
  });
};

export { uploadToStorage, writeResultsAndUploadToStorage };
