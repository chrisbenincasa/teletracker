import request from 'request-promise';
import cheerio from 'cheerio';
import { USER_AGENT_STRING } from '../common/constants';
import { sequentialPromises, wait } from '../common/promise_utils';
import fs from 'fs';

const reg = /.*netflix\.reactContext\s*=\s*(.*)/gm;

const doScrape = html => {
  let $ = cheerio.load(html);

  let result = JSON.parse(
    $('head > script[type="application/ld+json"]')
      .contents()
      .text(),
  );

  let other = $('script').filter((i, el) => {
    return $(el)
      .contents()
      .text()
      .includes('netflix.reactContext');
  });
  if (other.length > 0) {
    let x = other
      .eq(0)
      .contents()
      .text();
    let json = reg.exec(x)[1].replace(/\\x([0-9a-f]{2})/gi, function(_, pair) {
      return String.fromCharCode(parseInt(pair, 16));
    });
    if (json.endsWith(';')) {
      json = json.substring(0, json.length - 1);
    }
    let parsed = JSON.parse(json);

    if (parsed) {
      console.log(parsed.models.nmTitle);
    }
  }
};

export const scrape = async event => {
  const url = event.url;
  await sequentialPromises([url], 1000, async item => {
    try {
      let response = await request({
        uri: item,
        headers: {
          'User-Agent': USER_AGENT_STRING,
        },
      });

      // let html = fs.readFileSync(event.path).toString('utf-8');
      doScrape(response);
    } catch (e) {
      console.error(e);
    }
  });
};
