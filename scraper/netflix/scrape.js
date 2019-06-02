var request = require("request");
var cheerio = require("cheerio");
var moment = require("moment");
var fs = require("fs");
const Entities = require("html-entities").AllHtmlEntities;
const entities = new Entities();

request(
  "https://media.netflix.com/gateway/v1/en/titles/upcoming",
  (erroer, response, body) => {
    let parsed = JSON.parse(body);

    let titles = parsed.items.map(item => {
      return {
        title: item.name,
        releaseYear: 2019,
        availableDate: item.sortDate,
        type: item.type === "series" ? "show" : "movie",
        network: "Netflix",
        status: "Arriving"
      };
    });

    // Export data into JSON file
    let currentDate = moment().format("YYYY-MM-DD");
    fs.writeFile(
      currentDate + "-netflix-originals-arrivals" + ".json",
      JSON.stringify(titles),
      "utf8",
      function(err) {
        if (err) {
          throw err;
        }
        console.log("complete");
      }
    );
  }
);
