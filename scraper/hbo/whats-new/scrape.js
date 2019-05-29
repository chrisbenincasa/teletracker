var request = require("request");
var cheerio = require("cheerio");
var moment = require("moment");
var fs = require("fs");
var _ = require("lodash");

const uaString =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36";

request(
  "https://www.hbo.com/whats-new-whats-leaving",
  {
    headers: {
      "User-Agent": uaString
    }
  },
  function(error, response, html) {
    if (!error && response.statusCode == 200) {
      var currentYear = new Date().getFullYear();

      var parsedResults = [];
      var $ = cheerio.load(html);

      var textSections = $(
        '.components\\/Band--band[data-bi-context=\'{"band":"Text"}\'] > div > div'
      );

      var textSectionContents = textSections.contents();

      var h4Indexes = textSectionContents
        .map((idx, el) => {
          if (el.type == "tag" && el.name == "h4") {
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
          .find("b")
          .contents()
          .map((_, e) => e.data)
          .get(0);

        if (title.startsWith("Starting") || title.startsWith("Ending")) {
          let status = title.startsWith("Starting") ? "Arriving" : "Expiring";
          let [_, month, day] = title.split(" ", 3);

          let arrivingAt = moment(
            `${currentYear} ${month} ${day}`,
            "YYYY MMMM DD"
          );

          let titlesAndYears = section
            .slice(1)
            .filter("p")
            .text()
            .split("\n");

          titlesAndYears.forEach(titleAndYear => {
            //Strip out the release year from title
            let title = titleAndYear.trim();
            let regExp = /\(([^)]+)\)/;
            let year = regExp.exec(titleAndYear);
            let releaseYear;

            if (year) {
              releaseYear = year[1].trim();
              title = title.replace(year[0], "").trim();
            } else {
              releaseYear = null;
            }

            titles.push({
              availableDate: arrivingAt.format("YYYY-MM-DD"),
              title,
              releaseYear,
              category: "Film",
              status: status,
              network: "HBO"
            });
          });
        }
      });

      // Export data into JSON file
      let currentDate = moment().format("YYYY-MM-DD");
      fs.writeFile(
        currentDate + "-hbo-changes" + ".json",
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
  }
);
