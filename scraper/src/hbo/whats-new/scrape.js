import request from 'request-promise';
import cheerio from 'cheerio';
import moment from 'moment';
import { uploadToS3 } from '../../common/storage';
import { createWriteStream } from '../../common/stream_utils';
import { isProduction } from '../../common/env';
import { DATA_BUCKET, USER_AGENT_STRING } from '../../common/constants';

const scrape = async () => {
  try {
    await request({
      uri: 'https://www.hbo.com/whats-new-whats-leaving',
      headers: {
        'User-Agent': USER_AGENT_STRING,
      },
    }).then(async function(html) {
      const currentYear = new Date().getFullYear();

      const $ = cheerio.load(html);

      let currentDate = moment().format('YYYY-MM-DD');
      let fileName = currentDate + '-hbo-whats-new' + '.json';
      let [path, stream, flush] = createWriteStream(fileName);

      const textSections = $(
        '.components\\/Band--band[data-bi-context=\'{"band":"Text"}\'] > div > div',
      );

      textSections.each((_, el) => {
        let $el = $(el);
        let title = $el
          .find('h4 > b')
          .first()
          .text();
        if (title.length === 0) {
          title = $el
            .find('h4')
            .first()
            .text();
        }

        if ((title && title.includes('Starting')) || title.includes('Ending')) {
          let status = title.includes('Starting') ? 'Arriving' : 'Expiring';
          let titleTokens = title.split(' ').filter(s => s.length > 0);

          let [month, day] = titleTokens.slice(
            Math.max(titleTokens.length - 2, 1),
          );

          let daysInMonth = moment(
            `${currentYear} ${month}`,
            'YYYY MMMM',
          ).daysInMonth();

          if (day > daysInMonth) {
            day = daysInMonth;
          }

          let arrivingAt = moment(
            `${currentYear} ${month} ${day}`,
            'YYYY MMMM DD',
          );

          let titlesAndYears = $el
            .find('p')
            .text()
            .split('\n');

          titlesAndYears.forEach(titleAndYear => {
            //Strip out the release year from title
            let title = titleAndYear.trim();
            let yearRegex = /\(([0-9)]+)\)/;
            let parensRegex = /\(([^)]+)\)/;
            let year = yearRegex.exec(titleAndYear);
            let releaseYear;

            if (year) {
              releaseYear = year[1].trim();
              title = title
                .replace(year[0], '')
                .replace(parensRegex, '')
                .trim();
            } else {
              releaseYear = null;
            }

            let parsedReleaseYear = parseInt(releaseYear);
            parsedReleaseYear = isNaN(parsedReleaseYear)
              ? undefined
              : parsedReleaseYear;

            stream.write(
              JSON.stringify({
                availableDate: arrivingAt.format('YYYY-MM-DD'),
                title,
                parsedReleaseYear,
                category: 'Film',
                status: status,
                network: 'HBO',
              }) + '\n',
            );
          });
        }
      });

      stream.close();

      await flush;

      // Export data into JSON file
      if (isProduction()) {
        await uploadToS3(
          DATA_BUCKET,
          `scrape-results/hbo/${currentDate}/whats-new/${fileName}`,
          path,
        );
      }
    });
  } catch (e) {
    console.error(e);
  }
};

export { scrape };
