import { scheduleTask } from '../common/task_publisher';

const schedule = async type => {
  let task;
  switch (type) {
    case 'movie':
      task = 'com.teletracker.tasks.scraper.LocateMoviePopularityDelta';
      break;
    case 'tv_series':
      task = 'com.teletracker.tasks.scraper.LocateShowPopularityDelta';
      break;
    case 'person':
      task = 'com.teletracker.tasks.scraper.LocatePersonPopularityDelta';
      break;
    default:
      console.error('Unrecognized type ' + type);
      return;
  }

  if (task) {
    return scheduleTask({
      clazz: task,
      args: {
        mod: 4,
      },
    });
  }
};

export const scrape = async event => {
  if (event.Records && event.Records.length > 0) {
    let record = event.Records[0];

    if (record.s3) {
      if (record.s3.object.key.includes('movie')) {
        return await schedule('movie');
      } else if (record.s3.object.key.includes('tv_series')) {
        return await schedule('tv_series');
      } else if (record.s3.object.key.includes('person')) {
        return await schedule('person');
      } else {
        throw new Error('Unrecognized type ' + record.s3.object.key);
      }
    }
  }
};

export const scheduleDirect = async event => {
  return schedule(event.type);
};
