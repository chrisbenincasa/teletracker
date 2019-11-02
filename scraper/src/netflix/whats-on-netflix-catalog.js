import * as request from 'request';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import moment from 'moment';
import { uploadToS3 } from '../common/storage';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';

const MOVIES_ENDPOINT =
  'https://www.whats-on-netflix.com/wp-content/plugins/whats-on-netflix/json/movie.json';

const SHOWS_ENDPOINT =
  'https://www.whats-on-netflix.com/wp-content/plugins/whats-on-netflix/json/tv.json';

const ORIGINALS_ENDPOINT =
  'https://www.whats-on-netflix.com/wp-content/plugins/whats-on-netflix/json/originals.json';

const scrapeType = async (typ, endpoint) => {
  let [path, stream, flush] = createWriteStream(`netflix-${typ}-catalog.json`);

  request
    .get(endpoint, {
      headers: {
        'User-Agent': USER_AGENT_STRING,
      },
      qs: {
        _: moment().unix() * 1000,
      },
    })
    .pipe(stream);

  let currentDate = moment().format('YYYY-MM-DD');

  await flush;

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/netflix/whats-on-netflix/${currentDate}/netflix-${typ}-catalog.json`,
      path,
    );
  }
};

export const scrape = async event => {
  let typ = event.type || 'all';

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
};
