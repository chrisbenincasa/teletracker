var request = require("request");
var cheerio = require("cheerio");
var moment = require("moment");
var fs = require("fs");

request("https://www.hulu.com/press/new-this-month/", function(
  error,
  response,
  html
) {
  if (!error && response.statusCode == 200) {
    var parsedResults = [];
    var $ = cheerio.load(html);
    var currentYear = new Date().getFullYear();

    $(".new-this-month__table-content.table-content tbody tr").each(function(
      i,
      element
    ) {
      //Process date of availability
      var date = $(this)
        .children()
        .eq(0)
        .text()
        .split(" ");

      var m = moment(
        "" + currentYear + " " + date[0] + " " + date[1],
        "YYYY MMMM DD"
      );

      var availableDate = m.format("YYYY-MM-DD");

      var show = $(this)
        .children()
        .eq(1)
        .find("em")
        .text();

      //Strip out the release year from title
      var regExp = /\(([^)]+)\)/;
      var year = regExp.exec(show);
      var releaseYear;
      if (year) {
        releaseYear = year[1];
        show = show.replace(year[0], "");
      } else {
        releaseYear = null;
      }

      //Strip out the network from title
      var provider = regExp.exec(show);
      var network;
      if (provider) {
        network = provider[1];
        network = network.replace("*", "");
        show = show.replace(provider[0], "");
      } else {
        network = "Hulu";
      }

      var notes = $(this)
        .children()
        .eq(1)
        .find("span")
        .text();
      var category = $(this)
        .children()
        .eq(2)
        .text();
      var status = $(this)
        .children()
        .eq(3)
        .text();
      var metadata = {
        availableDate: availableDate,
        name: show.trim(),
        releaseYear: releaseYear,
        notes: notes,
        category: category,
        network: network,
        status: status ? status.trim() : ""
      };

      // Push meta-data into parsedResults array
      parsedResults.push(metadata);
    });

    // Export data into JSON file
    var currentDate = moment().format("YYYY-MM-DD");
    fs.writeFile(
      currentDate + "-hulu-changes" + ".json",
      JSON.stringify(parsedResults),
      "utf8",
      function(err) {
        if (err) {
          throw err;
        }
        console.log("complete");
      }
    );
  }
});
