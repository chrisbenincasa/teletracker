import _ from 'lodash';
import moment from 'moment';
import request from 'request-promise';
import { createWriteStream } from '../../common/stream_utils';
import { resolveSecret } from '../../common/aws_utils';
import { DATA_BUCKET, USER_AGENT_STRING } from '../../common/constants';
import { getObjectS3, uploadToS3 } from '../../common/storage';
import { isProduction } from '../../common/env';
import AWS from 'aws-sdk';
import { DEFAULT_BANDS, DEFAULT_PARALLELISM } from './scheduler';
import { sequentialPromises } from '../../common/promise_utils';

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

const seriesRegex = new RegExp('/series/([A-z-0-9]+)-(' + uuidRegex + ')$');
const moviesRegex = new RegExp('/movie/([A-z-0-9]+)-(' + uuidRegex + ')$');

const headers = {
  Connection: 'keep-alive',
  Pragma: 'no-cache',
  'Cache-Control': 'no-cache',
  'Upgrade-Insecure-Requests': 1,
  'User-Agent': USER_AGENT_STRING,
  DNT: 1,
  Accept:
    'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3',
  'Accept-Encoding': 'gzip, deflate, br',
};

const scrapeSeriesJson = async (cookie, id) => {
  try {
    let json = await request({
      uri: `https://discover.hulu.com/content/v4/hubs/series/${id}?schema=9`,
      headers: {
        ...headers,
        Cookie: cookie,
      },
      gzip: true,
      json: true,
    });

    let seasons = _.find(json.components, { id: '94' });

    // console.log(json.components);

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

      // TODO: Support seasons in hulu catalog dump
      // let start = _.chain(availability || [])
      //   .filter(av => !_.isUndefined(av.start_date))
      //   .head()
      //   .value();
      // let end = _.chain(availability || [])
      //   .filter(av => !_.isUndefined(av.end_date))
      //   .head()
      //   .value();

      return {
        name: json.details.entity.name,
        genres: json.details.entity.genres,
        releaseYear: json.details.entity.premiere_date
          ? moment.utc(json.details.entity.premiere_date).year()
          : null,
        externalId: json.details.entity.id,
        type: 'show',
        network: 'Hulu',
        numSeasonsAvailable,
        availableOn: null,
        expiresOn: null,
      };
    }
  } catch (e) {
    console.error(e.message);
  }
};

const scrapeMovieJson = async (cookie, id) => {
  try {
    let json = await request({
      uri: `https://discover.hulu.com/content/v4/hubs/movie/${id}?schema=9`,
      headers: {
        ...headers,
        Cookie: cookie,
      },
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
        ? moment.utc(json.details.entity.premiere_date).year()
        : null,
      externalId: json.details.entity.id,
      type: 'movie',
      network: 'Hulu',
      availableOn: availableOn ? moment.utc(availableOn).format() : null,
      expiresOn: availableEnd ? moment.utc(availableEnd).format() : null,
    };
  } catch (e) {
    console.error(e.message);
  }
};

const scrape = async event => {
  console.log(`Got event: `, event);
  try {
    let huluCookie =
      process.env.HULU_COOKIE || (await resolveSecret('hulu-cookie'));

    let offset = event.offset || 0;
    let limit = event.limit || -1;

    let band = event.band;
    let mod = event.mod || DEFAULT_BANDS;
    let parallelism = event.parallelism || DEFAULT_PARALLELISM;

    let scheduleNext = event.scheduleNext;

    let now = moment();

    let nowString = now.format('YYYY-MM-DD');
    let fileName = nowString + '_hulu-catalog' + '.json';
    if (!_.isUndefined(mod) && !_.isUndefined(band)) {
      fileName = `${nowString}_hulu-catalog.${band}.json`;
    }

    let urls = await getObjectS3(
      DATA_BUCKET,
      `scrape-results/hulu/${nowString}/hulu-catalog-urls.txt`,
    ).then(body => body.toString('utf-8').split('\n'));

    let [path, stream, flush] = createWriteStream(fileName);

    let urlBand = urls
      .filter((item, idx) => {
        if (!_.isUndefined(mod) && !_.isUndefined(band)) {
          return idx % mod === band;
        } else {
          return true;
        }
      })
      .slice(offset, limit === -1 ? urls.length : offset + limit);

    let seriesResults = await sequentialPromises(
      urlBand.filter(text => text.includes('/series/')),
      100,
      async url => {
        let matches = seriesRegex.exec(url);
        let result;
        if (matches && matches.length > 0) {
          result = await scrapeSeriesJson(huluCookie, matches[2]);
        } else {
          console.error('Series url did not match regex', url);
        }

        if (result) {
          stream.write(JSON.stringify(result) + '\n');
        } else {
          console.error('Could not get series result for ' + url);
        }
      },
    );

    let movieResults = await sequentialPromises(
      urlBand.filter(text => {
        text.includes('/movie/');
      }),
      100,
      async url => {
        let matches = moviesRegex.exec(url);
        let result;
        if (matches && matches.length > 0) {
          result = await scrapeMovieJson(huluCookie, matches[2]);
        } else {
          console.error('Movie url did not match regex', url);
        }

        if (result) {
          stream.write(JSON.stringify(result) + '\n');
        } else {
          console.error('Could not get movie result for ' + url);
        }
      },
    );

    let allResults = [...seriesResults, ...movieResults];
    console.log(`Scraping ${allResults.length} total`);

    allResults = _.filter(allResults, _.negate(_.isUndefined));

    await Promise.all(allResults);
    stream.close();
    await flush;

    if (isProduction()) {
      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/hulu/${nowString}/catalog/${fileName}`,
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
          FunctionName: 'hulu-catalog',
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
  } catch (e) {
    console.error(e);
  }
};

export { scrape };
