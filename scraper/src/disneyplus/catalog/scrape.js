var request = require('request');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs');

export const scrape = () => {
  request(
    'https://threadreaderapp.com/thread/1183715553057239040.html',
    function(error, response, html) {
      if (!error && response.statusCode == 200) {
        var parsedResults = [];
        var $ = cheerio.load(html);
        var data = $('.content-tweet').map((_, el) =>
          $(el)
            .text()
            .trim(),
        );

        // Export data into JSON file
        var currentDate = moment().format('YYYY-MM-DD');
        // console.log(parsedResults);
        fs.writeFile(
          currentDate + '-disney-plus-catalog' + '.json',
          JSON.stringify(data),
          'utf8',
          function(err) {
            if (err) {
              throw err;
            }
            console.log('complete');
          },
        );
      }
    },
  );
};
