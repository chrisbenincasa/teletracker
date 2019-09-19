import request from 'request-promise';
import cheerio from 'cheerio';
import moment from 'moment';
import fs from 'fs';
import { substitute } from '../../common/berglas';
import * as _ from 'lodash';

/*
curl 'https://discover.hulu.com/content/v4/hubs/series/3944ff02-8772-43eb-bacc-10923d83f140?schema=9' 
  -H 'Connection: keep-alive' 
  -H 'Pragma: no-cache' 
  -H 'Cache-Control: no-cache' 
  -H 'Upgrade-Insecure-Requests: 1' 
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36' 
  -H 'Sec-Fetch-Mode: navigate' 
  -H 'Sec-Fetch-User: ?1' 
  -H 'DNT: 1' 
  -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*\/*;q=0.8,application/signed-exchange;v=b3' 
  -H 'Sec-Fetch-Site: none' 
  -H 'Accept-Encoding: gzip, deflate, br' 
  -H 'Accept-Language: en-US,en;q=0.9' 
  --compressed
*/

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const uuidRegex =
  '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';

const genresRegex = /\/genre\/([A-z\-]+)-?([0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12})$/;
const seriesRegex = new RegExp('/series/([A-z-0-9]+)-(' + uuidRegex + ')$');
const moviesRegex = new RegExp('/movie/([A-z-0-9]+)-(' + uuidRegex + ')$');

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';

const headers = {
  Connection: 'keep-alive',
  Pragma: 'no-cache',
  'Cache-Control': 'no-cache',
  'Upgrade-Insecure-Requests': 1,
  'User-Agent': uaString,
  DNT: 1,
  Accept:
    'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3',
  'Accept-Encoding': 'gzip, deflate, br',
};

const scrapeSeriesJson = async id => {
  try {
    let json = await request({
      uri: `https://discover.hulu.com/content/v4/hubs/series/${id}?schema=9`,
      headers: {
        ...headers,
        Cookie: process.env.HULU_COOKIE,
      },
      gzip: true,
      json: true,
    });

    let seasons = _.find(json.components, { id: '94' });

    if (seasons) {
      let availability = _.chain(seasons.items)
        .map('items')
        .flatten()
        .map('bundle')
        .map('availability')
        .value();

      let numSeasonsAvailable = _.filter(availability || [], {
        is_available: true,
      }).length;

      let start = _.chain(availability || [])
        .filter(av => !_.isUndefined(av.start_date))
        .head()
        .value();
      let end = _.chain(availability || [])
        .filter(av => !_.isUndefined(av.end_date))
        .head()
        .value();

      return {
        name: json.details.entity.name,
        genres: json.details.entity.genres,
        releaseYear: json.details.entity.premiere_date
          ? moment
              .utc(json.details.entity.premiere_date)
              .year()
              .toString()
          : null,
        externalId: json.details.entity.id,
        type: 'show',
        network: 'Hulu',
        numSeasonsAvailable,
        availableOn: start ? moment.utc(start).format() : null,
        expiresOn: end ? moment.utc(end).format() : null,
      };
    }
  } catch (e) {
    console.error(e);
    return;
  }
};

const createWriteStream = fileName => {
  const stream = fs.createWriteStream(fileName, 'utf-8');
  // return new Promise((resolve, reject) => {
  //   stream.on('finish', () => resolve(true));
  //   stream.on('error', reject);
  //   data.forEach(datum => {
  //     stream.write(JSON.stringify(datum) + '\n');
  //   });
  //   stream.close();
  // });
  return stream;
};

const writeAsLines = (fileName, data) => {
  return new Promise((resolve, reject) => {
    const stream = fs.createWriteStream(fileName, 'utf-8');
    stream.on('finish', () => resolve(true));
    stream.on('error', reject);
    data.forEach(datum => {
      stream.write(JSON.stringify(datum) + '\n');
    });
    stream.close();
  });
};

const scrapeMovieJson = async id => {
  try {
    let json = await request({
      uri: `https://discover.hulu.com/content/v4/hubs/movie/${id}?schema=9`,
      headers,
      gzip: true,
      json: true,
    });

    let availableOn = _.get(
      json,
      'details.vod_items.focus.entity.bundle.availability.start_date',
    );
    let availableEnd = _.get(
      json,
      'details.vod_items.focus.entity.bundle.availability.end_date',
    );

    return {
      name: json.details.entity.name,
      genres: json.details.entity.genres,
      releaseYear: json.details.entity.premiere_date
        ? moment
            .utc(json.details.entity.premiere_date)
            .year()
            .toString()
        : null,
      externalId: json.details.entity.id,
      type: 'movie',
      network: 'Hulu',
      availableOn: availableOn ? moment.utc(availableOn).format() : null,
      expiresOn: availableEnd ? moment.utc(availableEnd).format() : null,
    };
  } catch (e) {
    console.error(e);
    return;
  }
};

const getAllSiteMaps = async () => {
  let html = await request({
    uri: 'https://www.hulu.com/sitemap_index.xml',
    headers: {
      'User-Agent': uaString,
    },
  });

  let $ = cheerio.load(html);

  return $('sitemap > loc')
    .map((idx, el) => $(el).text())
    .get();
};

const loadSiteMap = async sitemap => {
  let html = await request({
    uri: sitemap,
    headers: {
      'User-Agent': uaString,
    },
  });

  let $ = cheerio.load(html);

  return $('urlset > url > loc')
    .map((idx, el) => $(el).text())
    .get();
};

const scrape = async () => {
  await substitute();

  let sitemaps = await getAllSiteMaps();

  let prefix = process.env.NODE_ENV === 'production' ? '/tmp/' : '';

  let now = moment();
  let nowString = now.format('YYYY-MM-DD');
  let fileName = nowString + '_hulu-catalog' + '.json';

  // await writeAsLines(fileName, final);

  let stream = createWriteStream(fileName);

  let results = sitemaps.map(async sitemap => {
    let loadedSitemap = await loadSiteMap(sitemap);

    let allResults = [];

    let seriesResults = loadedSitemap
      .filter(text => text.includes('/series/'))
      .reduce(async (prev, url) => {
        let last = await prev;

        let matches = seriesRegex.exec(url);
        let result;
        if (matches && matches.length > 0) {
          result = await scrapeSeriesJson(matches[2]);
        }

        if (result) {
          stream.write(JSON.stringify(result) + '\n');
        }

        await wait(250);
        return [...last, result];
      }, Promise.resolve([]));

    allResults = allResults.concat(await seriesResults);

    let movieResults = loadedSitemap
      .filter(text => text.includes('/movie/'))
      .reduce(async (prev, url) => {
        let last = await prev;

        let matches = moviesRegex.exec(url);
        let result;
        if (matches && matches.length > 0) {
          result = await scrapeMovieJson(matches[2]);
        }

        if (result) {
          stream.write(JSON.stringify(result) + '\n');
        }

        await wait(250);
        return [...last, result];
      }, Promise.resolve([]));

    allResults = allResults.concat(await movieResults);

    return _.filter(allResults, _.negate(_.isUndefined));
  });

  await Promise.all(results);
  stream.close();
};

export { scrape };
