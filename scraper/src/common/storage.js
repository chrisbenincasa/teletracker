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

export const uploadStringToS3 = async (
  bucket,
  key,
  string,
  contentType = 'text/plain',
) => {
  console.log(`Uploading to s3://${bucket}/${key}`);

  return s3
    .upload({
      Bucket: bucket,
      Key: key,
      Body: string,
      ContentType: contentType,
    })
    .promise();
};

export const deleteS3Object = async (bucket, key) => {
  return s3
    .deleteObject({
      Bucket: bucket,
      Key: key,
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
    })
    .catch(e => {
      if (e.code === 'NoSuchKey') {
        console.error('Could not find key ' + key);
      }

      throw e;
    });
};

export const getDirectoryS3 = async (bucket, prefix) => {
  const getDirectoryS3Inner = async (bucket, prefix, token, acc) => {
    return s3
      .listObjectsV2({
        Bucket: bucket,
        Prefix: prefix,
        ContinuationToken: token,
      })
      .promise()
      .then(res => {
        let nextAcc = acc.concat(res.Contents);
        if (res.IsTruncated) {
          return getDirectoryS3Inner(
            bucket,
            prefix,
            res.NextContinuationToken,
            nextAcc,
          );
        } else {
          return nextAcc;
        }
      });
  };

  return getDirectoryS3Inner(bucket, prefix, null, []);
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
