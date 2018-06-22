var request = require('request');
var cheerio = require('cheerio');
var moment = require('moment');
var fs = require('fs');

request('https://www.hbo.com/movies/catalog', function (error, response, html) {
  if (!error && response.statusCode == 200) {
    var parsedResults = [];
    var $ = cheerio.load(html);    
    var data = $('.catalogband.basecomponent .render.bandjson').data('band-json');

    // Export data into JSON file
    var currentDate = moment().format('YYYY-MM-DD');
    // console.log(parsedResults);
    fs.writeFile(currentDate + '-hbo-movies' + '.json', JSON.stringify(data), 'utf8',function(err) {
      if (err) {
        throw err;
      }
      console.log('complete');
      }
    );
    
  }
});