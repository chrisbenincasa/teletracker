import {
  deleteS3Object,
  getDirectoryS3,
  getObjectS3,
  objectExists,
  uploadStringToS3,
} from '../../common/storage';
import { DATA_BUCKET } from '../../common/constants';
import moment from 'moment';
import { scheduleTask } from '../../common/task_publisher';

const grabLock = async (today) => {
  await uploadStringToS3(
    DATA_BUCKET,
    `scrape-results/hulu/${today}/catalog-ingest.lock`,
    'lock',
  );
  console.log('Successfully grabbed the lock.');
};

const releaseLock = async (today) => {
  await deleteS3Object(
    DATA_BUCKET,
    `scrape-results/hulu/${today}/catalog-ingest.lock`,
  );
  console.log('Successfully released the lock.');
};

const writeSentinel = async (today: string) => {
  await uploadStringToS3(
    DATA_BUCKET,
    `scrape-results/hulu/${today}/catalog-processing.lock`,
    'lock',
  );
  console.log('Successfully wrote sentinel object.');
};

export default async function watch(event) {
  try {
    let expectedSize = process.env.EXPECTED_SIZE || event.expectedSize;
    let force = Boolean(event.force);

    if (!expectedSize) {
      throw new Error('Need to pass expected size');
    }

    let today = moment().format('YYYY-MM-DD');

    if (
      await objectExists(
        DATA_BUCKET,
        `scrape-results/hulu/${today}/catalog-processing.lock`,
      )
    ) {
      console.log('Catalog is already processing. Skipping checks');
    }

    try {
      await getObjectS3(
        DATA_BUCKET,
        `scrape-results/hulu/${today}/catalog-ingest.lock`,
      );

      if (!force) {
        console.error('Catalog ingest lock already written. Skipping.');
        return;
      } else {
        console.log('Found lock, but continuing because force=true');
      }
    } catch (e) {
      if (e.code === 'NoSuchKey') {
        console.log('Did not find existing lock file. Continuing.');
      } else {
        throw e;
      }
    }

    await grabLock(today);

    let foundObjects = await getDirectoryS3(
      DATA_BUCKET,
      `scrape-results/hulu/${today}/catalog`,
    );

    console.log(`Found ${foundObjects.length} in the target directory`);

    if (foundObjects.length >= expectedSize) {
      let payload = {
        clazz: 'com.teletracker.tasks.scraper.hulu.HuluCatalogConcatenate',
        args: {
          source: `s3://${DATA_BUCKET}/scrape-results/hulu/${today}/catalog/`,
          destination: `s3://${DATA_BUCKET}/scrape-results/hulu/${today}/${today}_hulu-catalog.all.json`,
          scheduleIngestJob: true,
        },
      };

      try {
        await writeSentinel(today);
        await scheduleTask(payload);
      } catch (e) {
        console.error('Unable to schedule task', e);
      } finally {
        await releaseLock(today);
      }
    } else {
      await releaseLock(today);
    }
  } catch (e) {
    console.error(e);
    throw e;
  }
}
