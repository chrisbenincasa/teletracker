var request = require('request');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs');

export const scrape = () => {
  var $ = cheerio.load(fs.readFileSync('disneyplus/catalog/disneyplus.html'));

  var data = [];
  $('.content-tweet').map((index, el) => {
    // There are only 630 valid items on the page but there are other tweets captured in the above map (promo tweets etc), this only gets the actual items
    if (index < 630 && index !== 0) {
      data.push(
        $(el)
          .text()
          .trim(),
      );
    }
  });

  // Export data into JSON file
  var currentDate = moment().format('YYYY-MM-DD');

  fs.writeFileSync(
    currentDate + '-disney-plus-catalog' + '.json',
    JSON.stringify(data),
    'utf8',
  );
};
