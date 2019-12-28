import * as request from 'request';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import moment from 'moment';
import { uploadToS3 } from '../common/storage';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';
import { promises as fs } from 'fs';

const MOVIES_ENDPOINT =
  'https://www.whats-on-netflix.com/wp-content/plugins/whats-on-netflix/json/movie.json';

const SHOWS_ENDPOINT =
  'https://www.whats-on-netflix.com/wp-content/plugins/whats-on-netflix/json/tv.json';

const ORIGINALS_ENDPOINT =
  'https://www.whats-on-netflix.com/wp-content/plugins/whats-on-netflix/json/originals.json';

const scrapeType = async (typ, endpoint) => {
  try {
    let [path, stream, flush] = createWriteStream(
      `netflix-${typ}-catalog.json`,
    );
    let [tmpPath, tmpStream, tmpFlush] = createWriteStream(
      `netflix-${typ}-catalog-temp-catalog.json`,
    );

    request
      .get(endpoint, {
        headers: {
          'User-Agent': USER_AGENT_STRING,
        },
        qs: {
          _: moment().unix() * 1000,
        },
      })
      .pipe(tmpStream);

    await tmpFlush;

    let blob = JSON.parse((await fs.readFile(tmpPath)).toString('utf-8'));

    blob.forEach(line => {
      stream.write(JSON.stringify(line, null, 0) + '\n');
    });

    stream.close();

    await flush;

    let currentDate = moment().format('YYYY-MM-DD');

    if (isProduction()) {
      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/netflix/whats-on-netflix/${currentDate}/netflix-${typ}-catalog.json`,
        path,
      );
    }
  } catch (e) {
    console.error(e);
    throw e;
  }
};

export const scrape = async event => {
  let typ = event.type || 'all';

  console.log('Scraping whats-on-netflix type ' + typ);

  try {
    await Promise.all([
      typ === 'movie' || typ === 'all'
        ? scrapeType('movie', MOVIES_ENDPOINT)
        : Promise.resolve(),
      typ === 'tv' || typ === 'all'
        ? scrapeType('tv', SHOWS_ENDPOINT)
        : Promise.resolve(),
      typ === 'originals' || typ === 'all'
        ? scrapeType('originals', ORIGINALS_ENDPOINT)
        : Promise.resolve(),
    ]);
  } catch (e) {
    console.error(e);

    throw e;
  }
};
