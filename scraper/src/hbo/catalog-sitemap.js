import request from 'request-promise';
import * as cheerio from 'cheerio';
import {getFilePath} from "../common/tmp_files";
import * as fs from "fs";
import {uploadToS3} from "../common/storage";
import moment from "moment";

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';

export const scrape = async (event, context) => {
  let sitemap = await request({
    uri: `https://www.hbo.com/sitemap.xml`,
    headers: {
      'User-Agent': uaString,
    },
  });

  let $ = cheerio.load(sitemap);

  let urls = $('urlset > url > loc')
    .map((idx, el) => $(el).text())
    .get();

  let filePath = getFilePath("hbo-catalog-urls.txt");

  let stream = fs.createWriteStream(filePath, 'utf-8');

  urls.forEach(url => {
    stream.write(url + '\n');
  });

  stream.close();

  let now = moment();
  let formatted = now.format('YYYY-MM-DD');

  if (process.env.NODE_ENV === 'production') {
    return uploadToS3('teletracker-data', `scraper-results/${formatted}/hbo-catalog-urls.txt`, filePath);
  }
};
