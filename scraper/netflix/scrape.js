var request = require("request-promise");
var cheerio = require("cheerio");
var moment = require("moment");
var fs = require("fs").promises;

const uaString =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36";

const scrape = async () => {
  let body = await request({
    uri: "https://media.netflix.com/gateway/v1/en/titles/upcoming",
    headers: {
      "User-Agent": uaString
    }
  });

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
  let fileName = currentDate + "-netflix-originals-arrivals" + ".json";

  if (process.env.NODE_ENV == "production") {
    const { Storage } = require("@google-cloud/storage");

    const storage = new Storage();
    const bucket = storage.bucket("teletracker");

    let file = bucket.file(fileName);

    await fs.writeFile(`/tmp/${fileName}`, JSON.stringify(titles), "utf8");

    return bucket.upload(`/tmp/${fileName}`, {
      gzip: true,
      contentType: "application/json",
      destination: "scrape-results/" + fileName
    });
  } else {
    return fs.writeFile(fileName, JSON.stringify(titles), "utf8");
  }
};

exports.scrape = scrape;
