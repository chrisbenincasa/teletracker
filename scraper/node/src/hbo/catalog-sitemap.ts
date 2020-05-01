import * as cheerio from 'cheerio';
import { uploadToS3 } from '../common/storage';
import moment from 'moment';
import { isProduction } from '../common/env';
import { DATA_BUCKET } from '../common/constants';
import { createWriteStream } from '../common/stream_utils';
import { httpGet } from '../common/request_utils';

export const OUTPUT_FILE_NAME = 'hbo-catalog-urls.txt';
export function catalogSitemapS3Key(date) {
  return `scrape-results/hbo/${date}/catalog/${OUTPUT_FILE_NAME}`;
}

export const scrape = async () => {
  let sitemap = await httpGet(`https://www.hbo.com/sitemap.xml`);

  let $ = cheerio.load(sitemap);

  let urls = $('urlset > url > loc')
    .map((idx, el) => $(el).text())
    .get();

  let [filePath, stream, flush] = createWriteStream(OUTPUT_FILE_NAME);

  urls.forEach((url) => {
    stream.write(url + '\n');
  });

  stream.close();

  await flush;

  let now = moment();
  let formatted = now.format('YYYY-MM-DD');

  if (isProduction()) {
    return await uploadToS3(
      DATA_BUCKET,
      catalogSitemapS3Key(formatted),
      filePath,
    );
  }
};
