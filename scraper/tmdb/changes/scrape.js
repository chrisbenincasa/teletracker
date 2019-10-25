import { PubSub } from '@google-cloud/pubsub';
import { Storage } from '@google-cloud/storage';
import fs from 'fs';
import moment from 'moment';
import request from 'request-promise';
import { substitute } from '../../common/berglas';
import { publishTaskMessage } from '../../common/publisher';

const pubsub = new PubSub({
  projectId: 'teletracker',
});

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const doLoop = async type => {
  let now = moment();
  let page = 1;
  let totalPages = 1;
  let allResults = [];
  do {
    let res = await request({
      uri: `https://api.themoviedb.org/3/${type}/changes`,
      qs: {
        api_key: process.env.TMDB_API_KEY,
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
  await substitute();

  let now = moment();

  let movieChanges = await doLoop('movie');
  await wait(250);
  let tvChanges = await doLoop('tv');
  await wait(250);
  let personChanges = await doLoop('person');

  const storage = new Storage();
  const bucket = storage.bucket('teletracker');

  let prefix = process.env.NODE_ENV === 'production' ? '/tmp/' : '';

  let nowString = now.format('YYYY-MM-DD');

  let movieName = nowString + '_movie-changes' + '.json';
  let showName = nowString + '_show-changes' + '.json';
  let personName = nowString + '_person-changes' + '.json';

  await writeAsLines(prefix + movieName, movieChanges);
  await writeAsLines(prefix + showName, tvChanges);
  await writeAsLines(prefix + personName, personChanges);

  [movieName, showName, personName].map(async file => {
    await bucket.upload(prefix + file, {
      gzip: true,
      contentType: 'application/json',
      destination: 'scrape-results/' + nowString + '/' + file,
    });
  });

  [
    ['MovieChangesDumpTask', movieName],
    ['TvChangesDumpTask', showName],
    ['PersonChangesDumpTask', personName],
  ].forEach(async ([task, file]) => {
    await publishTaskMessage(
      'com.teletracker.tasks.tmdb.export_tasks.' + task,
      {
        input: 'gs://teletracker/scrape-results/' + nowString + '/' + file,
      },
      ['tag/RequiresTmdbApi'],
    );
  });
};

export { scrape };
