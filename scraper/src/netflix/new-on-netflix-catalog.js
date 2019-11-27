import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import { uploadToS3 } from '../common/storage';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';
import * as cheerio from 'cheerio';
import request from 'request-promise';
import { wait } from '../common/promise_utils';
import _ from 'lodash';
import moment from 'moment';
import AWS from 'aws-sdk';
import {resolveSecret} from "../common/aws_utils";

const FunctionName = 'new-on-netflix-catalog';
const nameAndYearRegex = /^(.*)\s\((\d+)\)$/i;

// Format: [384 titles] - Showing 1 to 120
let totalCountRegex = /\[(\d+) titles]\s*-\s*Showing\s*(\d+)\s*to\s*(\d+)/i;

const scrapeType = async letter => {
  try {
    let proxyAddress = await resolveSecret('proxy-address');

    let pathLetter = letter;
    if (letter === 'all') {
      pathLetter = '';
    }

    let [path, stream, flush] = createWriteStream(
      `netflix-catalog-${letter}.json`,
    );

    let fetchMore = false;
    let offset;
    do {
      console.log('Fetching offset = ' + (offset || 0));

      let $ = await request
        .get(`https://usa.newonnetflix.info/catalog/a2z/all/${pathLetter}`, {
          headers: {
            'User-Agent': USER_AGENT_STRING,
          },
          proxy: proxyAddress,
          qs: {
            start: offset || 0,
          },
        })
        .then(body => cheerio.load(body));

      let els = $('section')
        .filter(
          (idx, el) =>
            (
              $(el)
                .text()
                .match(/\d+\stitles/gi) || []
            ).length > 0,
        )
        .map((idx, el) => $(el).text())
        .get();

      if (els.length >= 1) {
        let txt = els[0].trim();
        let matches = totalCountRegex.exec(txt);

        if (matches) {
          let [_, total, start, end, ...rest] = matches;
          total = parseInt(total);
          start = parseInt(start);
          end = parseInt(end);
          if (!isNaN(total) && !isNaN(start) && !isNaN(end)) {
            offset = end;
            fetchMore = total > end;
          }
        } else {
          break;
        }

        $('article.oldpost div:nth-child(2)').each((_, el) => {
          let $el = $(el);
          let infopop = $el.find(' .infopop');
          let nameAndYear = infopop.text().trim();
          let description = infopop.attr('title');
          let externalId = infopop
            .attr('href')
            .split('/')
            .slice(-1)[0];
          let rating = $el
            .find('span.ratingsblock')
            .text()
            .trim();

          let type;
          if (rating.startsWith('TV')) {
            type = 'Show';
          } else {
            type = 'Movie';
          }

          let matchResult = nameAndYearRegex.exec(nameAndYear);

          if (
            matchResult &&
            matchResult[1].trim().length &&
            matchResult[2].trim().length
          ) {
            let obj = {
              title: matchResult[1].trim(),
              type,
              releaseYear: parseInt(matchResult[2].trim()),
              network: 'Netflix',
              description: description.trim(),
              certification: rating,
              externalId,
            };

            stream.write(JSON.stringify(obj, null, 0) + '\n');
          }
        });
      } else {
        console.warn('Unexpected shit');
        fetchMore = false;
      }

      if (fetchMore) {
        await wait(500);
      }
    } while (fetchMore);

    stream.close();

    await flush;

    if (isProduction()) {
      let currentDate = moment().format('YYYY-MM-DD');

      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/netflix/new-on-netflix/${currentDate}/netflix-catalog-${letter}.json`,
        path,
      );
    }
  } catch (e) {
    console.error(e);
  }
};

const A_CHAR_CODE = 97;
const Z_CHAR_CODE = 122;

function getLetterRange(start, limit) {
  const charCode = start.charCodeAt(0);

  if (start === 'all') {
    let max;
    if (!limit || limit === -1) {
      max = Z_CHAR_CODE + 1;
    } else {
      max = Math.min(A_CHAR_CODE + limit - 1, Z_CHAR_CODE + 1);
    }

    return _.chain(['all'])
      .concat(_.range(A_CHAR_CODE, max).map(i => String.fromCharCode(i)))
      .value();
  } else if (charCode >= A_CHAR_CODE && charCode <= Z_CHAR_CODE) {
    if (limit === -1) {
      return _.range(charCode, Z_CHAR_CODE + 1).map(i => String.fromCharCode(i));
    } else {
      // Pick the range up to 'z'
      let maxLetter = Math.min(Z_CHAR_CODE + 1, charCode + limit);
      return _.range(charCode, maxLetter).map(i => String.fromCharCode(i));
    }
  } else {
    return [];
  }
}

export const scrape = async event => {
  try {
    console.log(event);

    let letter = event.letter;

    if (!letter) {
      let aToZ = getLetterRange('all');

      for (let i = 0; i < aToZ.length; i++) {
        console.log('Fetching titles from page: "' + aToZ[i] + '"');
        await scrapeType(aToZ[i]);
        await wait(1000);
      }
    } else {
      let limit = event.limit || -1;

      let nextLetter;
      let range = getLetterRange(letter, limit);

      let maxLetter = _.max(range);
      if (maxLetter) {
        if (maxLetter === 'all') {
          nextLetter = 'a';
        } else if (maxLetter.charCodeAt(0) + 1 < Z_CHAR_CODE) {
          nextLetter = String.fromCharCode(maxLetter.charCodeAt(0) + 1);
        }
      }

      if (range.length) {
        console.log(`Scraping from ${_.head(range)} to ${_.last(range)}`);

        for (let i = 0; i < range.length; i++) {
          console.log('Fetching titles from page: "' + range[i] + '"');
          await scrapeType(range[i]);
          await wait(1000);
        }

        // Either we didn't pass "scheduleNext", which is an implicit true, or we did and we use that value
        let scheduleNext;
        if (_.isUndefined(event.scheduleNext)) {
          scheduleNext = true;
        } else {
          scheduleNext = Boolean(event.scheduleNext);
        }

        // If we want to schedule and have more to do, do it.
        if (Boolean(scheduleNext) && nextLetter) {
          console.log('scheduling next');

          const lambda = new AWS.Lambda({
            region: 'us-west-1',
          });

          const result = await lambda
            .invoke({
              FunctionName,
              InvocationType: 'Event',
              Payload: Buffer.from(
                JSON.stringify({ scheduleNext, letter: nextLetter, limit }),
                'utf-8',
              ),
            })
            .promise();

          console.log(result);
        }
      }
    }
  } catch (e) {
    console.error(e);
  }
};
