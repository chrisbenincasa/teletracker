var request = require('request-promise');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs');
var _ = require('lodash');
import {
  writeResultsAndUploadToStorage,
  writeResultsAndUploadToS3,
} from '../../common/storage';

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';

const createWriteStream = fileName => {
  const stream = fs.createWriteStream(fileName, 'utf-8');
  return stream;
};

const scrape = async () => {
  return request({
    uri: 'https://www.hbo.com/whats-new-whats-leaving',
    headers: {
      'User-Agent': uaString,
    },
  }).then(async function(html) {
    var currentYear = new Date().getFullYear();

    var $ = cheerio.load(html);

    let currentDate = moment().format('YYYY-MM-DD');
    let fileName = currentDate + '-hbo-changes' + '.json';
    let stream = createWriteStream(fileName);

    var textSections = $(
      '.components\\/Band--band[data-bi-context=\'{"band":"Text"}\'] > div > div',
    );

    var textSectionContents = textSections.contents();

    var h4Indexes = textSectionContents
      .map((idx, el) => {
        if (el.type == 'tag' && el.name == 'h4') {
          return idx;
        }
      })
      .get();

    var ranges = _.zip(h4Indexes, _.tail(h4Indexes));

    let titles = [];

    _.forEach(ranges, ([start, end]) => {
      var section = textSectionContents.slice(start, end);
      var title = section
        .first()
        .find('b')
        .contents()
        .map((_, e) => e.data)
        .get(0);

      if (title.includes('Starting') || title.includes('Ending')) {
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

        console.log(currentYear, month, day);

        let titlesAndYears = section
          .slice(1)
          .filter('p')
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

          titles.push({
            availableDate: arrivingAt.format('YYYY-MM-DD'),
            title,
            parsedReleaseYear,
            category: 'Film',
            status: status,
            network: 'HBO',
          });
        });
      }
    });

    stream.close();

    // Export data into JSON file
    if (process.env.NODE_ENV == 'production') {
      if (process.env.BACKEND === 'aws') {
        await writeResultsAndUploadToS3(
          fileName,
          'scrape-results/' + currentDate,
          titles,
        );
      } else {
        let [file, _] = await writeResultsAndUploadToStorage(
          fileName,
          'scrape-results/' + currentDate,
          titles,
        );
      }
    } else {
      const stream = fs.createWriteStream(fileName, 'utf-8');
      _.chain(titles)
        .sortBy(r => r.title)
        .each(r => {
          stream.write(JSON.stringify(r) + '\n');
        })
        .value();

      stream.close();
    }
  });
};

export { scrape };
