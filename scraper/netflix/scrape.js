var request = require('request-promise');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs').promises;
import { uploadToStorage } from '../common/storage';

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const scrape = async () => {
  let body = await request({
    uri: 'https://media.netflix.com/gateway/v1/en/titles/upcoming',
    headers: {
      'User-Agent': uaString,
    },
  });

  let parsed = JSON.parse(body);

  parsed.items.reduce(async (prev, x) => {
    let last = await prev;

    let result;
    try {
      let response = await request({
        uri: 'https://netflix.com/title/' + x.id,
        headers: {
          'User-Agent': uaString,
        },
      });

      let $ = cheerio.load(response);

      result = JSON.parse($('head > script[type="application/ld+json]').text());
      console.log(result);

      await wait(1000);

      return [...last, result];
    } catch (e) {
      console.error(e.statusCode);
    }

    return [...last, result];
  }, Promise.resolve([]));

  let titles = parsed.items.map(item => {
    return {
      title: item.name,
      releaseYear: 2019,
      availableDate: item.sortDate,
      type: item.type === 'series' ? 'show' : 'movie',
      network: 'Netflix',
      status: 'Arriving',
      externalId: item.id,
    };
  });

  // Export data into JSON file
  let currentDate = moment().format('YYYY-MM-DD');
  let fileName = currentDate + '-netflix-originals-arrivals' + '.json';

  if (process.env.NODE_ENV == 'production') {
    let [file, _] = await uploadToStorage(
      fileName,
      'scrape-results/' + currentDate,
      titles,
    );
  } else {
    return fs.writeFile(fileName, JSON.stringify(titles), 'utf8');
  }
};

export { scrape };
