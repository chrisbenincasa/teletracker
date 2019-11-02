import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import { getObjectS3, uploadToS3 } from '../common/storage';
import _ from 'lodash';
import request from 'request-promise';
import moment from 'moment';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';

export const scrape = async () => {
  let currentDate = moment().format('YYYY-MM-DD');
  let fileName = currentDate + '-netflix-originals-arrivals.json';
  let [filePath, stream, flush] = createWriteStream(fileName);

  let allNetflixOriginals = await getObjectS3(
    DATA_BUCKET,
    `scrape-results/netflix/whats-on-netflix/${currentDate}/netflix-originals-catalog.json`,
  ).then(buf => {
    return JSON.parse(buf.toString('utf-8'));
  });

  let netflixOriginalById = _.groupBy(allNetflixOriginals, 'netflixid');

  let body = await request({
    uri: 'https://media.netflix.com/gateway/v1/en/titles/upcoming',
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let parsed = JSON.parse(body);

  let titles = await parsed.items
    .filter(item => {
      return !moment(item.sortDate, 'YYYY-MM-DD').isAfter(
        moment().add(1, 'year'),
      );
    })
    .reduce(async (prev, item) => {
      let last = await prev;

      let result = {
        title: item.name,
        releaseYear: moment(item.sortDate, 'YYYY-MM-DD').year(),
        availableDate: item.sortDate,
        type: item.type === 'series' ? 'show' : 'movie',
        network: 'Netflix',
        status: 'Arriving',
        externalId: item.id,
      };

      // try {
      //   let response = await request({
      //     uri: 'https://netflix.com/title/' + x.id,
      //     headers: {
      //       'User-Agent': uaString,
      //     },
      //   });

      //   let $ = cheerio.load(response);

      //   result = JSON.parse(
      //     $('head > script[type="application/ld+json"]')
      //       .contents()
      //       .text(),
      //   );
      //   console.log(result);

      //   await wait(5000);

      //   return [...last, result];
      // } catch (e) {
      //   console.error(e.statusCode);
      // }

      // try {
      //   let response = await requestUnogs(
      //     'http://unogs.com/nf.cgi',
      //     {
      //       ...unogsQuery,
      //       q: item.id,
      //     },
      //     {
      //       Referer: `http://unogs.com/video/?v=${item.id}`,
      //     },
      //   );

      //   if (response.length > 0) {
      //     let parsed = JSON.parse(response);
      //     console.log(parsed);
      //     if (parsed.RESULT && parsed.RESULT.nfInfo) {
      //       let [
      //         bgImage,
      //         title,
      //         description,
      //         unknown,
      //         rating,
      //         unknown2,
      //         type,
      //         lastUpdated,
      //         firstSeen,
      //         releaseYear,
      //         id,
      //         ...rest
      //       ] = parsed.RESULT.nfInfo;

      //       console.log(parsed.RESULT.nfInfo);

      //       if (releaseYear) {
      //         result.releaseYear = releaseYear;
      //       }
      //     }
      //   }
      // } catch (e) {
      //   console.error(e);
      // }

      if (netflixOriginalById[item.id]) {
        let catalogItem = netflixOriginalById[item.id][0];
        result.releaseYear = Number(catalogItem.titlereleased);
        result.rating = catalogItem.rating;
        result.alternateTitle = catalogItem.title;
        result.description = catalogItem.description;
        result.actors = catalogItem.actors.split(',').map(s => s.trim());
      }

      stream.write(JSON.stringify(result) + '\n');

      return [...last, result];
    }, Promise.resolve([]));

  await Promise.all(titles);

  stream.close();

  await flush;

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/${currentDate}/${fileName}`,
      filePath,
    );
  }
};
