import { promises as fsPromises } from 'fs';
import fs from 'fs';
import AWS from 'aws-sdk';
import _ from 'lodash';

const s3 = new AWS.S3();

const writeResultsAndUploadToStorage = async (
  fileName,
  destinationDir,
  results,
) => {
  return writeResultsAndUploadToS3(fileName, destinationDir, results);
};

const uploadToStorage = async (fileName, destinationDir) => {
  return uploadToStorage(
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
