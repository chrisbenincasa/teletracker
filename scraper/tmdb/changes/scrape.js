import request from "request-promise";
import moment from "moment";
import fs from "fs";
import { substitute } from "../../common/berglas";
import { Storage } from "@google-cloud/storage";
import { PubSub } from "@google-cloud/pubsub";

const pubsub = new PubSub({
  projectId: "teletracker"
});

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

const getTopic = async topicName => {
  try {
    return await pubsub.createTopic(topicName);
  } catch (e) {
    if (e.code === 6) {
      return await pubsub.topic(topicName);
    } else {
      throw e;
    }
  }
};

const scrape = async () => {
  await substitute();

  let now = moment();

  let movieChanges = await doLoop("movie");
  await wait(250);
  let tvChanges = await doLoop("tv");
  await wait(250);
  let personChanges = await doLoop("person");

  const storage = new Storage();
  const bucket = storage.bucket("teletracker");

  let prefix = process.env.NODE_ENV === "production" ? "/tmp/" : "";

  let nowString = now.format("YYYY-MM-DD");

  let movieName = nowString + "_movie-changes" + ".json";
  let showName = nowString + "_show-changes" + ".json";
  let personName = nowString + "_person-changes" + ".json";

  await writeAsLines(prefix + movieName, movieChanges);
  await writeAsLines(prefix + showName, tvChanges);
  await writeAsLines(prefix + personName, personChanges);

  [movieName, showName, personName].map(async file => {
    await bucket.upload(prefix + file, {
      gzip: true,
      contentType: "application/json",
      destination: "scrape-results/" + nowString + "/" + file
    });
  });

  [
    ["ImportMoviesFromDump", movieName],
    ["ImportTvShowsFromDump", showName],
    ["ImportPeopleFromDump", personName]
  ].forEach(async ([task, file]) => {
    let payload = {
      clazz: "com.teletracker.tasks.tmdb.import_tasks." + task,
      args: {
        input: "gs://teletracker/" + file
      }
    };
    let topic = await getTopic("teletracker-task-queue");
    await topic.publisher.publish(Buffer.from(JSON.stringify(payload)));
  });
};

export { scrape };
