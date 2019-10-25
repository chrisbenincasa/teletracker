import { Storage } from '@google-cloud/storage';
import { promises as fsPromises } from 'fs';
import fs from 'fs';
import AWS from 'aws-sdk';

const storage = new Storage();
const s3 = new AWS.S3();
const bucket = storage.bucket('teletracker');

const writeResultsAndUploadToStorage = async (
  fileName,
  destinationDir,
  results,
) => {
  await fsPromises.writeFile(
    `/tmp/${fileName}`,
    JSON.stringify(results),
    'utf8',
  );

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

const writeResultsAndUploadToS3 = async (fileName, destinationDir, results) => {
  await fsPromises.writeFile(
    `/tmp/${fileName}`,
    JSON.stringify(results),
    'utf8',
  );

  return uploadToS3(
    'teletracker-data',
    `${destinationDir}/${fileName}`,
    fileName,
  );
};

const uploadToS3 = async (bucket, key, fileName) => {
  let sanitized = fileName.replace(/^\/tmp\//gi, '');
  return s3
    .putObject({
      Bucket: bucket,
      Key: key,
      Body: fs.createReadStream(`/tmp/${sanitized}`),
    })
    .promise();
};

export {
  uploadToStorage,
  writeResultsAndUploadToStorage,
  writeResultsAndUploadToS3,
  uploadToS3,
};
