import request from 'request-promise';
import cheerio from 'cheerio';
import { USER_AGENT_STRING } from '../common/constants';
import { sequentialPromises, wait } from '../common/promise_utils';
import fs from 'fs';

export const scrape = async event => {
  let movies = JSON.parse(
    fs
      .readFileSync('/Users/christianbenincasa/Desktop/netflix-movies.json')
      .toString('utf-8'),
  );

  let urls = movies.itemListElement.map(el => el.item.url);

  console.log(urls);

  // await sequentialPromises(urls.slice(0, event.limit), 1000, async item => {
  //   try {
  //     let response = await request({
  //       uri: item,
  //       headers: {
  //         'User-Agent': USER_AGENT_STRING,
  //       },
  //     });
  //
  //     let $ = cheerio.load(response);
  //
  //     let result = JSON.parse(
  //       $('head > script[type="application/ld+json"]')
  //         .contents()
  //         .text(),
  //     );
  //
  //     console.log(result);
  //   } catch (e) {
  //     console.error(e.statusCode);
  //   }
  // });
};
