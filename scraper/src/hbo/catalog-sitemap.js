import request from 'request-promise';
import * as cheerio from 'cheerio';
import { uploadToS3 } from '../common/storage';
import moment from 'moment';
import { isProduction } from '../common/env';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import { createWriteStream } from '../common/stream_utils';

const OUTPUT_FILE_NAME = 'hbo-catalog-urls.txt';

export const scrape = async (event, context) => {
  let sitemap = await request({
    uri: `https://www.hbo.com/sitemap.xml`,
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let $ = cheerio.load(sitemap);

  let urls = $('urlset > url > loc')
    .map((idx, el) => $(el).text())
    .get();

  let [filePath, stream, flush] = createWriteStream(OUTPUT_FILE_NAME);

  urls.forEach(url => {
    stream.write(url + '\n');
  });

  stream.close();

  await flush;

  let now = moment();
  let formatted = now.format('YYYY-MM-DD');

  if (isProduction()) {
    return await uploadToS3(
      DATA_BUCKET,
      `scrape-results/${formatted}/${OUTPUT_FILE_NAME}`,
      filePath,
    );
  }
};
