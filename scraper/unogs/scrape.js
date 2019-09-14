var request = require("request-promise");
var cheerio = require("cheerio");
var moment = require("moment");
var fs = require("fs").promises;
const Entities = require("html-entities").AllHtmlEntities;
const entities = new Entities();
import { uploadToStorage } from "../common/storage";

/**

curl -s 'https://unogs.com/nf.cgi?u=5unogs&q=get:exp:78&t=ns&cl=21,23&st=adv&ob=&p=0&l=1000&inc=&ao=and' \ 
  -H $'Cookie: cooksess=lv58tc0shun9jq3qgn1o0ft7u6; PHPSESSID=p8qbslkv9oma62ujrhrl684l45; sstring=get%3Aexp%3A78-\u21and' \ 
  -H 'DNT: 1' -H 'Accept-Encoding: gzip, deflate, br' \ 
  -H 'Accept-Language: en-US,en;q=0.9' \ 
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36' \ 
  -H 'Accept: application/json, text/javascript, *\/*; q=0.01' \
  -H 'Referer: https://unogs.com/countrydetail/' \
  -H 'X-Requested-With: XMLHttpRequest' 
  -H 'Connection: keep-alive' --compressed
**/

const headers = {
  Cookie:
    "cooksess=lv58tc0shun9jq3qgn1o0ft7u6; PHPSESSID=p8qbslkv9oma62ujrhrl684l45; sstring=get%3Aexp%3A78-\\u21and",
  "Accept-Encoding": "gzip, deflate, br",
  "User-Agent":
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36",
  Accept: "application/json, text/javascript, */*; q=0.0",
  Referer: "https://unogs.com/countrydetail/",
  "X-Requested-With": "XMLHttpRequest"
};

const query = {
  u: "5unogs",
  q: "get:exp:78",
  t: "ns",
  cl: "21",
  st: "adv",
  ob: "",
  p: "0",
  l: 1000,
  inc: "",
  ao: "and"
};

const scrape = async () => {
  let body = await request({
    uri: "https://unogs.com/nf.cgi",
    headers,
    qs: query
  });

  let parsed = JSON.parse(body);

  let titles = parsed.ITEMS.map(item => {
    let [
      netflixId,
      title,
      deepLink,
      htmlDesc,
      _1,
      _2,
      seriesOrMovie,
      releaseYear,
      ...rest
    ] = item;

    var $ = cheerio.load(htmlDesc);

    let expiration = $("b")
      .text()
      .replace("Expires on ", "")
      .trim();

    let parsed = moment(expiration, "YYYY-MM-DD");

    var metadata = {
      availableDate: parsed.format("YYYY-MM-DD"),
      title: entities.decode(title),
      releaseYear: releaseYear,
      // notes: notes,
      // category: category,
      type: seriesOrMovie === "movie" ? "movie" : "show",
      network: "Netflix",
      status: "Expiring"
    };

    return metadata;
  });

  let currentDate = moment().format("YYYY-MM-DD");
  let fileName = currentDate + "-netflix-expiring" + ".json";
  if (process.env.NODE_ENV == "production") {
    let [file, _] = await uploadToStorage(
      fileName,
      "scrape-results/" + currentDate,
      titles
    );
  } else {
    return fs.writeFile(fileName, JSON.stringify(titles), "utf8");
  }
};

export { scrape };
