import { getDirectoryS3, getObjectS3 } from '../../common/storage';
import { DATA_BUCKET } from '../../common/constants';
import moment from 'moment';
import { scheduleTask } from '../../common/task_publisher';

export default async function watch(event) {
  try {
    if (!event.expectedSize) {
      throw new Error('Need to pass expected size');
    }

    let today = moment().format('YYYY-MM-DD');

    try {
      await getObjectS3(
        DATA_BUCKET,
        `scrape-results/hulu/${today}/catalog/${today}_hulu-catalog.all.json`,
      );
      console.error('Concatenated output already exists for day');
      return;
    } catch (e) {
      if (e.code === 'NoSuchKey') {
        console.log('Did not find existing concatenated file. Continuing.');
      } else {
        throw e;
      }
    }

    let foundObjects = await getDirectoryS3(
      DATA_BUCKET,
      `scrape-results/hulu/${today}/catalog`,
    );

    console.log(foundObjects.length);

    if (foundObjects.length >= event.expectedSize) {
      let payload = {
        clazz: 'com.teletracker.tasks.scraper.hulu.HuluCatalogConcatenate',
        args: {
          source: `scrape-results/hulu/${today}/catalog`,
          destination: `scrape-results/hulu/${today}/catalog/${today}_hulu-catalog.all.json`,
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
