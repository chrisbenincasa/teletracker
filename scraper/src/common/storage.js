import { promises as fsPromises } from 'fs';
import fs from 'fs';
import AWS from 'aws-sdk';
import _ from 'lodash';
import zlib from 'zlib';

const s3 = new AWS.S3();

const writeResultsAndUploadToStorage = async (
  fileName,
  destinationDir,
  results,
) => {
  return writeResultsAndUploadToS3(fileName, destinationDir, results);
};

const uploadToStorage = async (fileName, destinationDir) => {
  let sanitized = fileName.replace(/^\/tmp\//gi, '');
  return uploadToS3(
    'teletracker-data',
    destinationDir + '/' + sanitized,
    fileName,
  );
};

const writeResultsAndUploadToS3 = async (fileName, destinationDir, results) => {
  await fsPromises.writeFile(fileName, JSON.stringify(results), 'utf8');

  return uploadToS3(
    'teletracker-data',
    `${destinationDir}/${fileName}`,
    fileName,
  );
};

const uploadToS3 = async (
  bucket,
  key,
  fileName,
  contentType = 'text/plain',
  gzip = false,
) => {
  let sanitized = fileName.replace(/^\/tmp\//gi, '');
  let stream = fs.createReadStream(`/tmp/${sanitized}`);
  if (gzip) {
    stream = stream.pipe(zlib.createGzip());
  }

  return uploadStreamToS3(bucket, key, stream, contentType, gzip);
};

export const uploadStreamToS3 = async (
  bucket,
  key,
  stream,
  contentType = 'text/plain',
  gzip = false,
) => {
  console.log(`Uploading to s3://${bucket}/${key}`);

  return s3
    .upload({
      Bucket: bucket,
      Key: key,
      Body: stream,
      ContentType: contentType,
      ContentEncoding: gzip ? 'gzip' : undefined,
    })
    .promise();
};

const getObjectS3 = async (bucket, key) => {
  return s3
    .getObject({
      Bucket: bucket,
      Key: key,
    })
    .promise()
    .then(res => {
      return res.Body;
    });
};

const fetchMostRecentFromS3 = async (bucket, key) => {
  let results = [];
  let continuationToken;
  do {
    let resp = await s3
      .listObjectsV2({
        Bucket: bucket,
        Prefix: key,
        ContinuationToken: continuationToken,
      })
      .promise();
    results = results.concat(resp.Contents);
    continuationToken = resp.NextContinuationToken;
  } while (Boolean(continuationToken));

  if (results.length > 0) {
    return _.chain(results)
      .sortBy(r => r.LastModified)
      .reverse()
      .value()[0];
  }
};

export {
  uploadToStorage,
  writeResultsAndUploadToStorage,
  writeResultsAndUploadToS3,
  uploadToS3,
  getObjectS3,
};
