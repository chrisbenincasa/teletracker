import request from "request-promise";
import cheerio from "cheerio";
import moment from "moment";
import { promises as fs } from "fs";
import { writeResultsAndUploadToStorage } from "../../common/storage";
import { scheduleJob } from "../../common/api";
import { substitute } from "../../common/berglas";

const uaString =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36";

const scrape = async () => {
  await substitute();

  let html = await request({
    uri: "https://www.hulu.com/press/new-this-month/",
    headers: {
      "User-Agent": uaString
    }
  });

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
      title: show.trim(),
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
  let fileName = currentDate + "-hulu-changes" + ".json";

  if (process.env.NODE_ENV == "production") {
    if (!process.env.API_HOST) {
      return Promise.reject(
        new Error("Could not find value for API_HOST variable")
      );
    }

    let [file, _] = await writeResultsAndUploadToStorage(
      fileName,
      "scrape-results/" + currentDate,
      parsedResults
    );

    return scheduleJob(file.name);
  } else {
    return fs.writeFile(fileName, JSON.stringify(parsedResults), "utf8");
  }
};

export { scrape };
