import { scheduleTask } from '../common/task_publisher';

export const scrape = async event => {
  if (event.Records && event.Records.length > 0) {
    let record = event.Records[0];

    if (record.s3) {
      let task;
      if (record.s3.object.key.includes('movie')) {
        task = 'com.teletracker.tasks.scraper.LocateMoviePopularityDelta';
      } else if (record.s3.object.key.includes('tv_series')) {
        task = 'com.teletracker.tasks.scraper.LocateShowPopularityDelta';
      } else if (record.s3.object.key.includes('person')) {
        task = 'com.teletracker.tasks.scraper.LocatePersonPopularityDelta';
      }

      if (task) {
        await scheduleTask({
          clazz: task,
          args: {
            mod: 4,
          },
        });
      }
    }
  }
};

export const scheduleDirect = async => {};
