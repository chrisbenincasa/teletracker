import AWS from 'aws-sdk';
import _ from 'lodash';

export const DEFAULT_PARALLELISM = 4;
export const DEFAULT_BANDS = 32;

export const schedule = async (event) => {
  const lambda = new AWS.Lambda({
    region: process.env.AWS_REGION,
  });

  let parallelism =
    event.parallelism || process.env.PARALLELISM || DEFAULT_PARALLELISM;
  let mod = event.bands || process.env.BANDS || DEFAULT_BANDS;
  let scheduleNext;
  if (_.isUndefined(event.scheduleNext)) {
    scheduleNext = true;
  } else {
    scheduleNext = Boolean(event.scheduleNext);
  }

  let chunks = mod / parallelism;

  let all = _.range(0, Math.floor(mod / chunks)).map(async (band) => {
    await lambda
      .invoke({
        FunctionName: 'hulu-catalog',
        InvocationType: 'Event',
        Payload: Buffer.from(
          JSON.stringify({ mod, band, parallelism, scheduleNext }),
          'utf-8',
        ),
      })
      .promise();
  });

  return Promise.all(all);
};

export const scheduleFromS3 = async (event) => {
  return schedule(event);
};
