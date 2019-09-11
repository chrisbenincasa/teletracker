import request from "request-promise";
import moment from "moment";
import fs from "fs";

const wait = ms => {
  return new Promise(resolve => {
    setTimeout(() => {
      resolve();
    });
  }, ms);
};

const doLoop = async type => {
  let now = moment();
  let yesterday = now.subtract(1, "day");

  let page = 1;
  let totalPages = 1;
  let allResults = [];
  do {
    let res = await request({
      uri: `https://api.themoviedb.org/3/${type}/changes`,
      qs: {
        api_key: process.env.TMDB_API_KEY
      },
      json: true
    });

    allResults = allResults.concat(res.results);
    totalPages = res.total_pages;
    page++;
    await wait(250);
  } while (page <= totalPages);

  return allResults;
};

const writeAsLines = (fileName, data) => {
  return new Promise((resolve, reject) => {
    const stream = fs.createWriteStream(fileName, "utf-8");
    stream.on("finish", () => resolve(true));
    stream.on("error", reject);
    data.forEach(datum => {
      stream.write(JSON.stringify(datum) + "\n");
    });
    stream.close();
  });
};

const scrape = async () => {
  let now = moment();

  let movieChanges = await doLoop("movie");
  await wait(250);
  let tvChanges = await doLoop("tv");
  await wait(250);
  let personChanges = await doLoop("person");

  const { Storage } = require("@google-cloud/storage");

  const storage = new Storage();
  const bucket = storage.bucket("teletracker");

  let movieName = now.format("YYYY-MM-DD") + "_movie-changes" + ".json";
  let showName = now.format("YYYY-MM-DD") + "_show-changes" + ".json";
  let personName = now.format("YYYY-MM-DD") + "_person-changes" + ".json";

  await writeAsLines(movieName, movieChanges);
  await writeAsLines(showName, tvChanges);
  await writeAsLines(personName, personChanges);

  [movieName, showName, personName].forEach(async file => {
    await bucket.upload(file, {
      gzip: true,
      contentType: "application/json",
      destination: "scrape-results/" + now.format("YYYY-MM-DD") + "/" + file
    });
  });
};

export { scrape };
