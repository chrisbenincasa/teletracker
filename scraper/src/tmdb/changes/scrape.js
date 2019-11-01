import fs from 'fs';
import moment from 'moment';
import request from 'request-promise';
import { uploadToS3 } from '../../common/storage';
import AWS from 'aws-sdk';

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const doLoop = async (apiKey, type) => {
  let now = moment();
  let page = 1;
  let totalPages = 1;
  let allResults = [];
  do {
    let res = await request({
      uri: `https://api.themoviedb.org/3/${type}/changes`,
      qs: {
        api_key: apiKey,
        start_date: moment()
          .subtract(1, 'days')
          .format('YYYY-MM-DD'),
      },
      json: true,
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
    const stream = fs.createWriteStream(fileName, 'utf-8');
    stream.on('finish', () => resolve(true));
    stream.on('error', reject);
    data.forEach(datum => {
      stream.write(JSON.stringify(datum) + '\n');
    });
    stream.close();
  });
};

const scrape = async () => {
  let ssm = new AWS.SSM();

  let apiKey = await ssm
    .getParameter({
      Name: 'tmdb-api-key-qa',
      WithDecryption: true,
    })
    .promise()
    .then(param => param.Parameter.Value);

  console.log('got api key ' + apiKey);

  let now = moment();

  let movieChanges = await doLoop(apiKey, 'movie');
  await wait(250);
  let tvChanges = await doLoop(apiKey, 'tv');
  await wait(250);
  let personChanges = await doLoop(apiKey, 'person');

  let prefix = process.env.NODE_ENV === 'production' ? '/tmp/' : '';

  let nowString = now.format('YYYY-MM-DD');

  let movieName = nowString + '_movie-changes' + '.json';
  let showName = nowString + '_show-changes' + '.json';
  let personName = nowString + '_person-changes' + '.json';

  await writeAsLines(prefix + movieName, movieChanges);
  await writeAsLines(prefix + showName, tvChanges);
  await writeAsLines(prefix + personName, personChanges);

  let allUploads = [movieName, showName, personName].map(async file => {
    await uploadToS3(
      'teletracker-data',
      `scrape-results/${nowString}/${file}`,
      file,
    );
  });

  await Promise.all(allUploads);

  // [movieName, showName, personName].map(async file => {
  //   await bucket.upload(prefix + file, {
  //     gzip: true,
  //     contentType: 'application/json',
  //     destination: 'scrape-results/' + nowString + '/' + file,
  //   });
  // });

  // [
  //   ['MovieChangesDumpTask', movieName],
  //   ['TvChangesDumpTask', showName],
  //   ['PersonChangesDumpTask', personName],
  // ].forEach(async ([task, file]) => {
  //   await publishTaskMessage(
  //     'com.teletracker.tasks.tmdb.export_tasks.' + task,
  //     {
  //       input: 'gs://teletracker/scrape-results/' + nowString + '/' + file,
  //     },
  //     ['tag/RequiresTmdbApi'],
  //   );
  // });
};

export { scrape };
