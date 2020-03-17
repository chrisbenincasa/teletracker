import request from 'request-promise';
import cheerio from 'cheerio';
import moment from 'moment';
import * as _ from 'lodash';
import { uploadToS3 } from '../../common/storage';
import { DATA_BUCKET, USER_AGENT_STRING } from '../../common/constants';
import { isProduction } from '../../common/env';
import { createWriteStream } from '../../common/stream_utils';

export const scrape = async event => {
  let pageName =
    event.pageName ||
    moment()
      .format('MMMM-YYYY')
      .toLowerCase();

  let html = await request({
    uri: `https://press.hulu.com/schedule/${pageName}/`,
    headers: {
      'User-Agent': USER_AGENT_STRING,
    },
  });

  let parsedResults = [];
  let $ = cheerio.load(html);
  let currentYear = new Date().getFullYear();

  $('#tableSchedule tbody tr').each(function(i, element) {
    //Process date of availability
    let $this = $(this);

    let date = $this
      .children()
      .eq(0)
      .text()
      .split(' ');

    let m = moment(
      '' + currentYear + ' ' + date[0] + ' ' + date[1],
      'YYYY MMMM DD',
    );

    let availableDate = m.format('YYYY-MM-DD');

    let show = $this
      .children()
      .eq(1)
      .text();
    console.log(show);

    //Strip out the release year from title
    let regExp = /\(([^)]+)\)/;
    let year = regExp.exec(show);
    let releaseYear;
    if (year) {
      releaseYear = year[1];
      show = show.replace(year[0], '');
    } else {
      releaseYear = null;
    }
    let parsedReleaseYear = parseInt(releaseYear);
    parsedReleaseYear = isNaN(parsedReleaseYear)
      ? undefined
      : parsedReleaseYear;

    //Strip out the network from title
    let provider = regExp.exec(show);
    let network;
    if (provider) {
      network = provider[1];
      network = network.replace('*', '');
      show = show.replace(provider[0], '');
    } else {
      network = 'Hulu';
    }

    let notes = $this
      .children()
      .eq(1)
      .text();

    let category = $this
      .children()
      .eq(2)
      .text();

    let status = $this
      .children()
      .eq(3)
      .text();

    let metadata = {
      availableDate: availableDate,
      title: show.trim(),
      releaseYear: parsedReleaseYear,
      notes: notes,
      category: category,
      network: network,
      status: status ? status.trim() : '',
    };

    // Push meta-data into parsedResults array
    parsedResults.push(metadata);
  });

  // Export data into JSON file
  let currentDate = moment().format('YYYY-MM-DD');
  let fileName = currentDate + '-hulu-changes' + '.json';
  let [filePath, stream, flush] = createWriteStream(fileName);

  _.chain(parsedResults)
    .sortBy(r => r.title)
    .each(r => {
      stream.write(JSON.stringify(r) + '\n');
    })
    .value();

  stream.close();

  await flush;

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/hulu/new/${currentDate}/${fileName}`,
      filePath,
    );
  }
};
