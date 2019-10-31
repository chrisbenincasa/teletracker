var request = require('request-promise');
import * as cheerio from 'cheerio';
var moment = require('moment');
var fsPromises = require('fs').promises;
import fs from 'fs';
import { uploadToStorage, getObjectS3 } from '../common/storage';
import requestUnogs, { unogsHeaders } from '../unogs/utils';
import _ from 'lodash';

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';

const unogsQuery = {
  u: '5unogs',
  t: 'loadvideo',
  cl: '78',
};

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const createWriteStream = fileName => {
  const stream = fs.createWriteStream(fileName, 'utf-8');
  return stream;
};

const scrape = async () => {
  let currentDate = moment().format('YYYY-MM-DD');
  let fileName = currentDate + '-netflix-originals-arrivals' + '.json';
  let stream = createWriteStream(fileName);

  let allNetflixOriginals = await getObjectS3(
    'teletracker-data',
    'scrape-results/netflix/whats-on-netflix/2019-10-29/netflix-originals-catalog.json',
  ).then(buf => {
    return JSON.parse(buf.toString('utf-8'));
  });

  let netflixOriginalById = _.groupBy(allNetflixOriginals, 'netflixid');

  let body = await request({
    uri: 'https://media.netflix.com/gateway/v1/en/titles/upcoming',
    headers: {
      'User-Agent': uaString,
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

      // await wait(100);

      return [...last, result];
    }, Promise.resolve([]));

  // Export data into JSON file

  if (process.env.NODE_ENV == 'production') {
    let [file, _] = await uploadToStorage(
      fileName,
      'scrape-results/' + currentDate,
      titles,
    );
  } else {
    stream.close();
  }
};

const testPage = async () => {
  let contents = await fsPromises.readFile(
    '/Users/christianbenincasa/Desktop/Seis Manos _ Netflix Official Site.htm',
  );

  let $ = cheerio.load(contents);

  console.log(
    JSON.parse(
      $('head > script[type="application/ld+json"]')
        .contents()
        .text(),
    ),
  );
};

export { scrape, testPage };
