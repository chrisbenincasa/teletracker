import fetch from 'node-fetch';
import { createWriteStream, createReadStream, stat as statCb } from 'fs';
import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3-node';
import moment from 'moment';
import { promisify } from 'util';
import * as stream from 'stream';

const stat = promisify(statCb);
const pipeline = promisify(stream.pipeline);

export async function handler() {
  const dryRun = process.env.DRY_RUN === 'true';

  await fetch('https://datasets.imdbws.com/title.ratings.tsv.gz').then(
    async (res) => {
      console.log(
        `Content-Length header reads: ${res.headers.get('content-length')}`,
      );

      await pipeline(res.body, createWriteStream('/tmp/imdb-ratings.tsv.gz'));
    },
  );

  console.log(`Finished downloading.`);

  const now = moment();
  const nowString = now.format('YYYY-MM-DD');

  const statResult = await stat('/tmp/imdb-ratings.tsv.gz');

  const length = statResult.size;

  console.log(`Downloaded size is ${length} bytes`);

  if (!dryRun) {
    const key = `scrape-results/imdb/${nowString}/ratings/ratings.tsv.gz`;

    console.log(`Uploading to ${key}`);

    const request = new PutObjectCommand({
      Bucket: process.env.BUCKET!,
      Key: key,
      ContentEncoding: 'gzip',
      ContentType: 'text/tab-separated-values',
      Body: createReadStream('/tmp/imdb-ratings.tsv.gz'),
      ContentLength: length,
    });

    const client = new S3Client({});

    try {
      const data = await client.send(request);
      console.log(data);
    } catch (e) {
      console.error(e);
    }
  }
}
