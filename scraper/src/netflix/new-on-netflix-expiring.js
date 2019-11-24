import fs from 'fs';
import cheerio from 'cheerio';
import moment from 'moment';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';
import { uploadToS3 } from '../common/storage';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import request from 'request-promise';

const URL = 'https://usa.newonnetflix.info/lastchance';
const nameAndYearRegex = /^(.*)\s\((\d+)\)$/i;

export const scrape = async event => {
  try {
    let html;
    if (event.file) {
      html = fs.readFileSync(event.file).toString('utf-8');
    } else {
      html = await request.get(URL, {
        headers: {
          'User-Agent': USER_AGENT_STRING,
        },
      });
    }

    const $ = cheerio.load(html);

    let [path, stream, flush] = createWriteStream(`netflix-expiring.json`);

    const found = $('section')
      .filter((_, el) => {
        const $el = $(el);
        const link = $el.find('a').first();
        if (link) {
          const nameOrId = link.attr('name') || link.attr('id');
          return nameOrId && nameOrId.startsWith('d');
        } else {
          return false;
        }
      })
      .get();

    let seenExternalIds = new Set();

    found.forEach(dateSection => {
      const $el = $(dateSection);
      const link = $el.find('a').first();
      const nameOrId = link.attr('name') || link.attr('id');

      const expiresOn = moment(nameOrId, '[d]YYYY-MM-DD');

      const sectionWithContent = $el.next('section');
      sectionWithContent
        .find('article.oldpost div:nth-child(2)')
        .each((_, el) => {
          let $el = $(el);
          let infopop = $el.find(' .infopop');
          let nameAndYear = infopop.text().trim();
          let description = infopop.attr('title');
          let externalId = infopop
            .attr('href')
            .split('/')
            .slice(-1)[0];

          let matchResult = nameAndYearRegex.exec(nameAndYear);

          if (
            !seenExternalIds.has(externalId) &&
            matchResult &&
            matchResult[1].trim().length &&
            matchResult[2].trim().length
          ) {
            seenExternalIds.add(externalId);

            let obj = {
              title: matchResult[1].trim(),
              // type,
              releaseYear: parseInt(matchResult[2].trim()),
              network: 'Netflix',
              description: description.trim(),
              status: 'Expiring',
              // certification: rating,
              externalId,
              availableDate: expiresOn.format('YYYY-MM-DD'),
            };

            stream.write(JSON.stringify(obj, null, 0) + '\n');
          }
        });
    });

    stream.close();
    await flush;

    if (isProduction()) {
      let currentDate = moment().format('YYYY-MM-DD');

      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/netflix/new-on-netflix/${currentDate}/netflix-expiring.json`,
        path,
      );
    }
  } catch (e) {
    console.error(e);
  }
};
