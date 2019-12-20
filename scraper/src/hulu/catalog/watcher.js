import {
  getDirectoryS3,
  getObjectS3,
  uploadStringToS3,
  uploadToS3,
} from '../../common/storage';
import { DATA_BUCKET } from '../../common/constants';
import moment from 'moment';
import { scheduleTask } from '../../common/task_publisher';

export default async function watch(event) {
  try {
    let expectedSize = process.env.EXPECTED_SIZE || event.expectedSize;

    if (!expectedSize) {
      throw new Error('Need to pass expected size');
    }

    let today = moment().format('YYYY-MM-DD');

    try {
      await getObjectS3(
        DATA_BUCKET,
        `scrape-results/hulu/${today}/catalog-ingest.lock`,
      );
      console.error('Catalog ingest lock already written. Skipping.');
      return;
    } catch (e) {
      if (e.code === 'NoSuchKey') {
        console.log('Did not find existing lock file. Continuing.');
      } else {
        throw e;
      }
    }

    await uploadStringToS3(
      DATA_BUCKET,
      `scrape-results/hulu/${today}/catalog-ingest.lock`,
      'lock',
    );

    let foundObjects = await getDirectoryS3(
      DATA_BUCKET,
      `scrape-results/hulu/${today}/catalog`,
    );

    console.log(foundObjects.length);

    if (foundObjects.length >= expectedSize) {
      let payload = {
        clazz: 'com.teletracker.tasks.scraper.hulu.HuluCatalogConcatenate',
        args: {
          source: `s3://${DATA_BUCKET}/scrape-results/hulu/${today}/catalog`,
          destination: `s3://${DATA_BUCKET}/scrape-results/hulu/${today}/${today}_hulu-catalog.all.json`,
          scheduleIngestJob: true,
        },
      };

      await scheduleTask(payload);
    }
  } catch (e) {
    console.error(e);
    throw e;
  }
}
