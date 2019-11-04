var request = require('request');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs');

request('https://www.hbo.com/series/all-series', function(
  error,
  response,
  html,
) {
  if (!error && response.statusCode == 200) {
    var parsedResults = [];
    var $ = cheerio.load(html);
    var data = $(
      '.seriesspecialssamplingcatalogband.basecomponent .render.bandjson',
    ).data('band-json');
    console.log(data);
    // Export data into JSON file
    var currentDate = moment().format('YYYY-MM-DD');
    // console.log(parsedResults);
    fs.writeFile(
      currentDate + '-hbo-series' + '.json',
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
});