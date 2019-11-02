import moment from 'moment';
import request from 'request-promise';
import { uploadToS3 } from '../../common/storage';
import { resolveSecret } from '../../common/aws_utils';
import { wait } from '../../common/promise_utils';
import { createWriteStream } from '../../common/stream_utils';

const doLoop = async (apiKey, type) => {
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
  let [_, stream, flush] = createWriteStream(fileName);
  data.forEach(datum => {
    stream.write(JSON.stringify(datum) + '\n');
  });
  stream.close();

  return flush;
};

const scrape = async () => {
  let apiKey = await resolveSecret('tmdb-api-key-qa');

  let now = moment();

  let movieChanges = await doLoop(apiKey, 'movie');
  await wait(250);
  let tvChanges = await doLoop(apiKey, 'tv');
  await wait(250);
  let personChanges = await doLoop(apiKey, 'person');

  let nowString = now.format('YYYY-MM-DD');

  let movieName = nowString + '_movie-changes.json';
  let showName = nowString + '_show-changes.json';
  let personName = nowString + '_person-changes.json';

  await Promise.all([
    writeAsLines(movieName, movieChanges),
    writeAsLines(showName, tvChanges),
    writeAsLines(personName, personChanges),
  ]);

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
