import cheerio from 'cheerio';
import _ from 'lodash';
import moment from 'moment';
import request from 'request-promise';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import { isProduction } from '../common/env';
import { sequentialPromises } from '../common/promise_utils';
import { getObjectS3, uploadToS3 } from '../common/storage';
import { createWriteStream } from '../common/stream_utils';
import {
  DEFAULT_BANDS,
  DEFAULT_PARALLELISM,
} from './originals-arriving-scheduler';
import AWS from 'aws-sdk';
import '../common/seq_utils';
import { filterMod } from '../common/seq_utils';

export type Args = {
  date?: string;
  offset?: number;
  limit?: number;
  mod?: number;
  band?: number;
  parallelism?: number;
  scheduleNext?: boolean;
};

export const scrape = async (event: Args) => {
  let currentDate = event.date || moment().format('YYYY-MM-DD');

  let offset = event.offset || 0;
  let limit = event.limit || -1;

  let band = event.band;
  let mod = event.mod || DEFAULT_BANDS;
  let parallelism = event.parallelism || DEFAULT_PARALLELISM;

  let scheduleNext = event.scheduleNext;

  let fileName = currentDate + '_netflix-originals-arrivals.json';
  if (!_.isUndefined(mod) && !_.isUndefined(band)) {
    fileName = `${currentDate}_netflix-originals-arrivals.${band}.json`;
  }
  let [filePath, stream, flush] = createWriteStream(fileName);

  let allNetflixOriginals = await getObjectS3(
    DATA_BUCKET,
    `scrape-results/netflix/whats-on-netflix/${currentDate}/netflix-originals-catalog.json`,
  ).then((buf) => {
    return buf
      .toString('utf-8')
      .split('\n')
      .filter((s) => s.length > 0)
      .map((line) => JSON.parse(line));
  });

  let netflixOriginalById = _.groupBy(allNetflixOriginals, 'netflixid');

  let body = await request({
    uri: 'https://media.netflix.com/gateway/v1/en/titles/upcoming',
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let parsed = JSON.parse(body);

  let filteredItems = parsed.items.filter((item) => {
    return !moment(item.sortDate, 'YYYY-MM-DD').isAfter(
      moment().add(1, 'year'),
    );
  });

  let titles = await sequentialPromises(
    filterMod(filteredItems, mod, band).page(offset, limit),
    1000,
    async (item: any) => {
      let result: any = {
        title: item.name,
        releaseYear: moment(item.sortDate, 'YYYY-MM-DD').year(),
        availableDate: item.sortDate,
        type: item.type === 'series' ? 'show' : 'movie',
        network: 'Netflix',
        status: 'Arriving',
        externalId: item.id,
      };

      let raw;
      try {
        let response = await request({
          uri: 'https://netflix.com/title/' + item.id,
          headers: {
            'User-Agent': USER_AGENT_STRING,
          },
        });

        let $ = cheerio.load(response);

        raw = JSON.parse(
          $('head > script[type="application/ld+json"]').contents().text(),
        );
      } catch (e) {
        console.error(e);
      }

      if (raw) {
        result.raw = raw;
      }

      if (netflixOriginalById[item.id]) {
        let catalogItem = netflixOriginalById[item.id][0];
        result.releaseYear = Number(catalogItem.titlereleased);
        result.rating = catalogItem.rating;
        result.alternateTitle = catalogItem.title;
        result.description = catalogItem.description;
        result.type = catalogItem.type;
        result.actors = catalogItem.actors.split(',').map((s) => s.trim());
      }

      stream.write(JSON.stringify(result) + '\n');

      return item;
    },
  );

  await Promise.all(titles);

  stream.close();

  await flush;

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/netflix/direct/${currentDate}/${fileName}`,
      filePath,
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
        FunctionName: 'netflix-arriving-originals',
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
};
