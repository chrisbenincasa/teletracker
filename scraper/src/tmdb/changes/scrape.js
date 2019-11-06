import moment from 'moment';
import request from 'request-promise';
import { uploadToS3 } from '../../common/storage';
import { resolveSecret } from '../../common/aws_utils';
import { wait } from '../../common/promise_utils';
import { createWriteStream } from '../../common/stream_utils';
import { isProduction } from '../../common/env';
import _ from 'lodash';
import { DATA_BUCKET } from '../../common/constants';
import { scheduleTask } from '../../common/task_publisher';

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

const scrape = async event => {
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

  if (isProduction()) {
    let allUploads = [
      [movieName, 'MovieChangesDumpTask'],
      [showName, 'TvChangesDumpTask'],
      [personName, 'PersonChangesDumpTask'],
    ].map(async ([file, taskName]) => {
      let key = `scrape-results/${nowString}/${file}`;
      await uploadToS3(DATA_BUCKET, key, file);

      if (_.isUndefined(event.scheduleTasks) || Boolean(event.scheduleTasks)) {
        let payload = {
          clazz: taskName,
          args: {
            input: `s3://${DATA_BUCKET}/${key}`,
            dryRun: false,
          },
          jobTags: ['tag/RequiresTmdbApi'],
        };

        await scheduleTask(payload);
      }
    });

    await Promise.all(allUploads);
  }
};

export { scrape };
