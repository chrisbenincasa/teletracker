var request = require('request');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs');

export const scrape = () => {
  var $ = cheerio.load(fs.readFileSync('disney-plus/catalog/disney-plus.html'));

  var data = [];
  var nameAndYearRegex = /^(.*)\s\((\d+)\)$/i;
  $('.content-tweet').each((index, el) => {
    // There are only 630 valid items on the page but there are other tweets captured in the above map (promo tweets etc), this only gets the actual items
    if (index < 630 && index !== 0) {
      var nameAndYear = $(el)
        .text()
        .trim();
      var matchResult = nameAndYearRegex.exec(nameAndYear);

      if (
        matchResult &&
        matchResult[1].trim().length &&
        matchResult[2].trim().length
      ) {
        let obj = {
          title: matchResult[1].trim(),
          type: undefined,
          releaseYear: parseInt(matchResult[2].trim()),
          network: 'disney-plus',
          description: undefined,
          certification: undefined,
          externalId: undefined,
        };
        data.push(JSON.stringify(obj, null, 0) + '\n');
      }
    }
  });

  // Export data into JSON file
  var currentDate = moment().format('YYYY-MM-DD');

  fs.writeFileSync(
    'disney-plus/catalog/' + currentDate + '-disney-plus-catalog' + '.json',
    data,
    'utf8',
  );
};
