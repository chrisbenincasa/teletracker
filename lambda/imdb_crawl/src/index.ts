import fetch from 'node-fetch';
import { createWriteStream, createReadStream, statSync } from 'fs';
import { S3Client, PutObjectCommand } from '@aws-sdk/client-s3-node';
import moment from 'moment';

export async function handler() {
  await fetch('https://datasets.imdbws.com/title.ratings.tsv.gz').then(
    (res) => {
      const dest = createWriteStream('/tmp/imdb-ratings.tsv.gz');
      res.body.pipe(dest);
      return new Promise((fulfill) => {
        res.body.on('finish', fulfill);
      });
    },
  );

  console.log(`Finished downloading.`);

  const now = moment();
  const nowString = now.format('YYYY-MM-DD');

  const length = statSync('/tmp/imdb-ratings.tsv.gz').size;

  const request = new PutObjectCommand({
    Bucket: process.env.BUCKET!,
    Key: `scrape-results/imdb/${nowString}/ratings/ratings.tsv.gz`,
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
