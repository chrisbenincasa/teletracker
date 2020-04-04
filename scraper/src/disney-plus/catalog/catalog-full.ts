import request from 'request-promise';
import { USER_AGENT_STRING } from '../../common/constants';
import cheerio from 'cheerio';
import _ from 'lodash';
import { createWriteStream } from '../../common/stream_utils';
import moment from 'moment';
import { sequentialPromises } from '../../common/promise_utils';

interface Event {
  pullSitemap: boolean;
  mod?: number;
  band?: number;
  offset?: number;
  limit?: number;
}

const BASE_URL = 'https://www.disneyplus.com';
const SITEMAP_BASE_URL = 'https://www.disneyplus.com/sitemap.xml';
const PAGE_SCRIPT_REGEX = /.*window\.__PRELOADED_STATE__\s*=\s*(.*);/m;
const URL_REGEX = /https?:\/\/(www.)?disneyplus.com\/(series|movie)\/([A-z0-9-])+\/(.+)$/;

const scrapePage = async (url) => {
  console.log(`Scraping ${url}`);

  let page = await request(url, {
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let $ = cheerio.load(page);

  let matchingScripts = $('script').filter((i, el) =>
    $(el).contents().text().includes('__PRELOADED_STATE__'),
  );

  if (matchingScripts.length > 0) {
    let id: string | undefined = _.chain(url).split('/').last().value();

    let rawScript = matchingScripts.eq(0).contents().text();
    let regexResult = PAGE_SCRIPT_REGEX.exec(rawScript);
    if (id && regexResult) {
      let pageJson = JSON.parse(regexResult[1]);
      let itemDetails = pageJson.details[id];

      let type = itemDetails.programType;
      let urlRegexMatch = URL_REGEX.exec(url);
      if (urlRegexMatch) {
        if (urlRegexMatch.length >= 2) {
          type = urlRegexMatch[2];
        }
      }

      if (itemDetails) {
        return {
          name: itemDetails.title,
          releaseDate: _.find(itemDetails.releases, { releaseType: 'original' })
            ?.releaseYear,
          externalId: id,
          type,
          description: itemDetails.description,
          url,
          network: 'Disney Plus',
        };
      }
    }
  }
};

const pullSiteMap = async (url) => {
  let sitemap = await request(url, {
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let $ = cheerio.load(sitemap);

  return $('loc')
    .filter(
      (i, el) =>
        $(el).contents().text().includes(`${BASE_URL}/movies`) ||
        $(el).contents().text().includes(`${BASE_URL}/series`),
    )
    .map((i, el) => $(el).contents().text())
    .toArray();
};

export default async function scrape(event: Event) {
  const offset = event.offset || 0;
  const limit = event.limit || -1;

  if (event.pullSitemap) {
    let sitemaps = await request(SITEMAP_BASE_URL, {
      headers: {
        'User-Agent': USER_AGENT_STRING,
      },
    });

    let $sitemap = cheerio.load(sitemaps);

    let entries = $sitemap('loc')
      .filter((i, el) => !$sitemap(el).contents().text().includes('static'))
      .map((i, el) => $sitemap(el).contents().text())
      .toArray();

    let allEntries = _.uniq(
      (await Promise.all(entries.map(pullSiteMap))).reduce(
        (prev, curr) => prev.concat(curr),
        [],
      ),
    );

    let now = moment();

    let nowString = now.format('YYYY-MM-DD');
    let fileName = nowString + '_disney-catalog' + '.json';
    if (!_.isUndefined(event.mod) && !_.isUndefined(event.band)) {
      fileName = `${nowString}_disney-catalog.${event.band}.json`;
    }
    let [path, stream, flush] = createWriteStream(fileName);

    let chunks = _.chain(allEntries)
      .slice(offset, limit === -1 ? allEntries.length : offset + limit)
      .chunk(5)
      .value();

    await sequentialPromises(chunks, 1000, async (urls) => {
      let results = await Promise.all(_.map(urls, scrapePage));
      if (results) {
        _.chain(results)
          .filter(_.negate(_.isUndefined))
          .each((r) => stream.write(JSON.stringify(r) + '\n'))
          .value();
        console.log(`Wrote ${results.length} results`);
      }
    });

    stream.close();

    await flush;
  }
}
