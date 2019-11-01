const request = require('request-promise');
const cheerio = require('cheerio');
const moment = require('moment');
const fsSync = require('fs');
const fs = require('fs').promises;
const Entities = require('html-entities').AllHtmlEntities;
import { uploadToStorage } from '../common/storage';
const entities = new Entities();

/**
 * curl $'http://unogs.com/nf.cgi?u=5unogs&q=-\u00211900,2019-\u00210,5-\u00210,10-\u00210,10-\u0021Any-\u0021Any-\u0021Any-\u0021Any-\u0021I%20Don&t=ns&cl=78&st=adv&ob=Relevance&p=1&l=100&inc=&ao=and'
 * -H 'Pragma: no-cache' -H 'DNT: 1'
 * -H 'Accept-Encoding: gzip, deflate'
 * -H 'Accept-Language: en-US,en;q=0.9'
 * -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36'
 * -H 'Accept: application/json, text/javascript, *\/*; q=0.01
 * -H 'Cache-Control: no-cache
 * -H 'X-Requested-With: XMLHttpRequest'
 * -H $'Cookie: cooksess=lv58tc0shun9jq3qgn1o0ft7u6; clist=78%2C; PHPSESSID=q9qecmnuvrcp4hm9kb24jn47s3; sstring=-\u00211900%2C2019-\u00210%2C5-\u00210%2C10-\u00210%2C10-\u0021Any-\u0021Any-\u0021Any-\u0021Any-\u0021I%20Don-\u0021and'
 * -H 'Connection: keep-alive
 * -H $'Referer: http://unogs.com/?q=-\u00211900,2019-\u00210,5-\u00210,10-\u00210,10-\u0021Any-\u0021Any-\u0021Any-\u0021Any-\u0021I%20Don&cl=78&st=adv&ob=Relevance&p=1&ao=and'
 * --compressed --insecure
 **/

const headers = {
  Accept: 'application/json, text/javascript, */*; q=0.01',
  Pragma: 'no-cache',
  DNT: 1,
  'Accept-Language': 'en-US,en;q=0.9',
  'Accept-Encoding': 'gzip, deflate',
  'Cache-Control': 'no-cache',
  Pragma: 'no-cache',
  'User-Agent':
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/76.0.3809.132 Safari/537.36',
  'X-Requested-With': 'XMLHttpRequest',
  Cookie:
    'cooksess=lv58tc0shun9jq3qgn1o0ft7u6; clist=78%2C; PHPSESSID=q9qecmnuvrcp4hm9kb24jn47s3; sstring=-!1900%2C2019-!0%2C5-!0%2C10-!0%2C10-!Any-!Any-!Any-!Any-!I%20Don-!and',
  Referer:
    'http://unogs.com/?q=-!1900,2019-!0,5-!0,10-!0,10-!Any-!Any-!Any-!Any-!I%20Don&cl=78&st=adv&ob=Relevance&p=1&ao=and',
};

const query = {
  u: '5unogs',
  q: '-!1900,2019-!0,5-!0,10-!0,10-!Any-!Any-!Any-!Any-!I Don',
  t: 'ns',
  cl: '78',
  st: 'adv',
  ob: 'Relevance',
  p: 1,
  l: 100,
  inc: '',
  ao: 'and',
};

const pullPage = async (page = 1, limit = 9999) => {
  let newQuery = {
    ...query,
    p: page,
  };

  if (page >= limit) {
    return [];
  }

  console.log('pulling page ' + page);

  let body = await request({
    uri: 'https://unogs.com/nf.cgi',
    headers,
    qs: newQuery,
  });

  let parsed = JSON.parse(body);

  let total = Number(parsed.COUNT);

  return parsed.ITEMS.map(item => {
    let [
      netflixId,
      title,
      deepLink,
      htmlDesc,
      _1,
      _2,
      seriesOrMovie,
      releaseYear,
      ...rest
    ] = item;

    let $ = cheerio.load(htmlDesc);

    let expiration = $('b')
      .text()
      .replace('Expires on ', '')
      .trim();

    let parsedReleaseYear = parseInt(releaseYear);
    parsedReleaseYear = isNaN(parsedReleaseYear)
      ? undefined
      : parsedReleaseYear;

    let metadata = {
      title: entities.decode(title),
      releaseYear: parsedReleaseYear,
      externalId: netflixId,
      type: seriesOrMovie === 'movie' ? 'movie' : 'show',
      network: 'Netflix',
      description: $('*').text(),
    };

    if (expiration.length) {
      let parsed = moment(expiration, 'YYYY-MM-DD');
      metadata.status = 'Expiring';
      metadata.availableDate = parsed.format('YYYY-MM-DD');
    }

    return metadata;
  });
};

const scrape = async () => {
  let currentDate = moment().format('YYYY-MM-DD');
  let fileName = currentDate + '-netflix-catalog-us' + '.json';
  let fileNameWithPrefix =
    process.env.NODE_ENV == 'production' ? '/tmp/' + fileName : fileName;

  if (fsSync.existsSync(fileNameWithPrefix)) {
    fsSync.unlinkSync(fileNameWithPrefix);
  }

  console.log('Writing results to ' + fileNameWithPrefix);

  let page = 1;
  let items = [];
  do {
    items = await pullPage(page);
    page++;
    let string = items.map(title => JSON.stringify(title, null, 0)).join('\n');
    await fs.appendFile(fileNameWithPrefix, string + '\n', 'utf8');
  } while (items.length > 0);

  if (process.env.NODE_ENV == 'production') {
    await uploadToStorage(fileName, 'scrape-results/' + currentDate);
  }
};

export { scrape };
