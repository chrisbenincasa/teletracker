import moment from 'moment';
import { createWriteStream } from '../../common/stream_utils';
import request from 'request-promise';
import { DATA_BUCKET, USER_AGENT_STRING } from '../../common/constants';
import cheerio from 'cheerio';
import { isProduction } from '../../common/env';
import { uploadToS3 } from '../../common/storage';
import { httpGet } from '../../common/request_utils';

const getAllSiteMaps = async () => {
  let html = await httpGet('https://www.hulu.com/sitemap_index.xml');

  let $ = cheerio.load(html);

  return $('sitemap > loc')
    .map((idx, el) => $(el).text())
    .get();
};

const loadSiteMap = async (sitemap: string) => {
  let html = await httpGet(sitemap);

  let $ = cheerio.load(html);

  return $('urlset > url > loc')
    .map((idx, el) => $(el).text())
    .get();
};

const uuidRegex =
  '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';
const seriesRegex = new RegExp('/series/([A-z-0-9]+)-(' + uuidRegex + ')$');
const moviesRegex = new RegExp('/movie/([A-z-0-9]+)-(' + uuidRegex + ')$');

export const scrape = async () => {
  try {
    let sitemaps = await getAllSiteMaps();

    let now = moment();
    let nowString = now.format('YYYY-MM-DD');
    let fileName = 'hulu-catalog-urls.txt';

    let [path, stream, flush] = createWriteStream(fileName);

    let results = sitemaps.map(async (sitemap) => {
      let loadedSitemap = await loadSiteMap(sitemap);
      loadedSitemap
        .filter((url) => seriesRegex.test(url) || moviesRegex.test(url))
        .forEach((url) => {
          stream.write(url + '\n');
        });
    });

    await Promise.all(results);
    stream.close();
    await flush;

    if (isProduction()) {
      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/hulu/${nowString}/${fileName}`,
        path,
      );
    }
  } catch (e) {
    console.error(e);
  }
};
