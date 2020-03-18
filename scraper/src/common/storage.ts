import fs, { promises as fsPromises } from 'fs';
import stream from 'stream';
import AWS from 'aws-sdk';
import _ from 'lodash';
import zlib from 'zlib';

const s3 = new AWS.S3();

export const writeResultsAndUploadToStorage = async (
  fileName: string,
  destinationDir: string,
  results: any,
) => {
  return writeResultsAndUploadToS3(fileName, destinationDir, results);
};

export const uploadToStorage = async (
  fileName: string,
  destinationDir: string,
) => {
  let sanitized = fileName.replace(/^\/tmp\//gi, '');
  return uploadToS3(
    'teletracker-data',
    destinationDir + '/' + sanitized,
    fileName,
  );
};

export const writeResultsAndUploadToS3 = async (
  fileName: string,
  destinationDir: string,
  results: any,
) => {
  await fsPromises.writeFile(fileName, JSON.stringify(results), 'utf8');

  return uploadToS3(
    'teletracker-data',
    `${destinationDir}/${fileName}`,
    fileName,
  );
};

export const uploadToS3 = async (
  bucket: string,
  key: string,
  fileName: string,
  contentType: string = 'text/plain',
  gzip: boolean = false,
) => {
  let sanitized = fileName.replace(/^\/tmp\//gi, '');
  let stream;
  if (gzip) {
    stream = fs.createReadStream(`/tmp/${sanitized}`).pipe(zlib.createGzip());
  } else {
    stream = fs.createReadStream(`/tmp/${sanitized}`);
  }

  return uploadStreamToS3(bucket, key, stream, contentType, gzip);
};

export const uploadStreamToS3 = async (
  bucket: string,
  key: string,
  stream: stream.Readable,
  contentType: string = 'text/plain',
  gzip: boolean = false,
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
  bucket: string,
  key: string,
  string: string,
  contentType: string = 'text/plain',
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

export const deleteS3Object = async (bucket: string, key: string) => {
  return s3
    .deleteObject({
      Bucket: bucket,
      Key: key,
    })
    .promise();
};

export const getObjectS3: (
  bucket: string,
  key: string,
) => Promise<Buffer> = async (bucket: string, key: string) => {
  return s3
    .getObject({
      Bucket: bucket,
      Key: key,
    })
    .promise()
    .then(res => {
      return res.Body as Buffer;
    })
    .catch(e => {
      if (e.code === 'NoSuchKey') {
        console.error('Could not find key ' + key);
      }

      throw e;
    });
};

export const getDirectoryS3 = async (bucket: string, prefix: string) => {
  const getDirectoryS3Inner = async (
    bucket: string,
    prefix: string,
    token: string | undefined,
    acc: AWS.S3.ObjectList,
  ) => {
    return s3
      .listObjectsV2({
        Bucket: bucket,
        Prefix: prefix,
        ContinuationToken: token,
      })
      .promise()
      .then(res => {
        let nextAcc = acc.concat(res.Contents || []);
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

  return getDirectoryS3Inner(bucket, prefix, undefined, []);
};

export const fetchMostRecentFromS3 = async (bucket: string, key: string) => {
  let results: AWS.S3.Object[] = [];
  let continuationToken;
  do {
    let resp = await s3
      .listObjectsV2({
        Bucket: bucket,
        Prefix: key,
        ContinuationToken: continuationToken,
      })
      .promise();

    if (resp.Contents) {
      results = results.concat(resp.Contents);
    }

    continuationToken = resp.NextContinuationToken;
  } while (Boolean(continuationToken));

  if (results.length > 0) {
    return _.chain(results)
      .sortBy(r => r.LastModified)
      .reverse()
      .value()[0];
  }
};
