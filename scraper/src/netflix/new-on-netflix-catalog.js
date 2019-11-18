import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import { uploadToS3 } from '../common/storage';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';
import * as cheerio from 'cheerio';
import request from 'request-promise';

const nameAndYearRegex = /^(.*)\s\((\d+)\)$/i;

// Format: [384 titles] - Showing 1 to 120
let totalCountRegex = /\[(\d+) titles]\s*-\s*Showing\s*(\d+)\s*to\s*(\d+)/i;

const scrapeType = async letter => {
  try {
    let [path, stream, flush] = createWriteStream(
      `netflix-catalog-${letter}.json`,
    );

    let fetchMore = false;
    let offset;
    do {
      console.log('Fetching offset = ' + (offset || 0));

      let $ = await request
        .get(`https://usa.newonnetflix.info/catalog/a2z/all/${letter}`, {
          headers: {
            'User-Agent': USER_AGENT_STRING,
          },
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
              name: matchResult[1].trim(),
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
    } while (fetchMore);

    stream.close();

    await flush;

    if (isProduction()) {
      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/netflix/whats-on-netflix/${currentDate}/netflix-${typ}-catalog.json`,
        path,
      );
    }
  } catch (e) {
    console.error(e);
  }
};

export const scrape = async event => {
  let letter = event.letter;

  if (!letter) {
    let aToZ = _.chain(['all'])
      .concat(_.range(97, 123).map(i => String.fromCharCode(i)))
      .value();

    for (let i = 0; i < aToZ.length; i++) {
      await scrapeType(aToZ[i]);
    }
  } else {
    return scrapeType(letter);
  }
};
