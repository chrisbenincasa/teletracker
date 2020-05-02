import request from 'request-promise';
import { DATA_BUCKET, USER_AGENT_STRING } from '../../common/constants';
import cheerio from 'cheerio';
import _ from 'lodash';
import { createWriteStream } from '../../common/stream_utils';
import moment from 'moment';
import { sequentialPromises } from '../../common/promise_utils';
import { isProduction } from '../../common/env';
import { getObjectS3, uploadToS3 } from '../../common/storage';
import AWS from 'aws-sdk';
import { filterMod } from '../../common/seq_utils';
import { BaseScrapeEvent } from '../../types/types';

interface Event extends BaseScrapeEvent {
  pullSitemap: boolean;
}

interface SiteMapPullEvent {
  returnResults?: boolean;
}

const SITEMAP_FILE_NAME = 'disney-plus-catalog-urls.txt';
const BASE_URL = 'https://www.disneyplus.com';
const SITEMAP_BASE_URL = 'https://www.disneyplus.com/sitemap.xml';
const PAGE_SCRIPT_REGEX = /.*window\.__PRELOADED_STATE__\s*=\s*(.*);/m;
const URL_REGEX = /https?:\/\/(www.)?disneyplus.com\/(series|movie)\/([A-z0-9-])+\/(.+)$/;

const pullUrlList = async (url) => {
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
    .toArray()
    .map((i, el) => $(el).contents().text());
};

export function catalogSitemapS3Key(date: string) {
  return `scrape-results/disney/${date}/catalog/${SITEMAP_FILE_NAME}`;
}

const pullSiteMap = async (event: SiteMapPullEvent) => {
  let [filePath, stream, flush] = createWriteStream(SITEMAP_FILE_NAME);

  let sitemaps = await request(SITEMAP_BASE_URL, {
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let $sitemap = cheerio.load(sitemaps);

  let entries = $sitemap('loc')
    .filter((i, el) => !$sitemap(el).contents().text().includes('static'))
    .toArray()
    .map((i, el) => $sitemap(el).contents().text());

  const seen = new Set<string>();

  const allEntries = (
    await sequentialPromises(entries, undefined, async (url) => {
      let urls = await pullUrlList(url);
      return urls.filter((url) => {
        if (!seen.has(url)) {
          seen.add(url);
          return true;
        } else {
          return false;
        }
      });
    })
  ).reduce((prev, curr) => prev.concat(curr), []);

  allEntries.forEach((line) => stream.write(line + '\n'));

  stream.close();
  await flush;

  let now = moment();
  let formatted = now.format('YYYY-MM-DD');

  if (isProduction()) {
    await uploadToS3(DATA_BUCKET, catalogSitemapS3Key(formatted), filePath);
  }

  if (event.returnResults) {
    return allEntries;
  }
};

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

export default async function scrape(event: Event) {
  const mod = event.mod;
  const band = event.band;
  const offset = event.offset || 0;
  const limit = event.limit || -1;
  const parallelism = event.parallelism || Number(process.env.PARALLELISM);
  const scheduleNext = event.scheduleNext;

  let allEntries: string[];
  let now = moment();
  let nowString = now.format('YYYY-MM-DD');

  if (event.pullSitemap) {
    allEntries = (await pullSiteMap({ returnResults: true }))!;
  } else {
    allEntries = await getObjectS3(
      DATA_BUCKET,
      catalogSitemapS3Key(nowString),
    ).then((body) => {
      return body.toString('utf-8').split('\n');
    });
  }

  allEntries = filterMod(allEntries, mod, band);

  let fileName = nowString + '_disney-catalog' + '.json';
  if (!_.isUndefined(mod) && !_.isUndefined(band)) {
    fileName = `${nowString}_disney-catalog.${band}.json`;
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

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/disney/${nowString}/catalog/${fileName}`,
      path,
    );
  }

  if (
    Boolean(scheduleNext) &&
    _.isNumber(mod) &&
    _.isNumber(band) &&
    _.isNumber(parallelism) &&
    band + parallelism < mod
  ) {
    const lambda = new AWS.Lambda({
      region: process.env.AWS_REGION,
    });

    await lambda
      .invoke({
        FunctionName: 'disney-plus-catalog',
        InvocationType: 'Event',
        Payload: Buffer.from(
          JSON.stringify({
            mod,
            band: band + parallelism,
            parallelism,
            scheduleNext,
          }),
          'utf-8',
        ),
      })
      .promise();
  }
}
