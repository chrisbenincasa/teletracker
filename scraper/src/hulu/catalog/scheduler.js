import AWS from 'aws-sdk';
import _ from 'lodash';

export const schedule = async event => {
  const lambda = new AWS.Lambda({
    region: 'us-west-1',
  });

  let parallelism = event.parallelism || process.env.PARALLELISM || 4;
  let mod = event.bands || process.env.BANDS || 32;
  let scheduleNext;
  if (_.isUndefined(event.scheduleNext)) {
    scheduleNext = true;
  } else {
    scheduleNext = Boolean(event.scheduleNext);
  }

  let chunks = mod / parallelism;

  let all = _.range(0, Math.floor(mod / chunks)).map(async i => {
    await lambda
      .invoke({
        FunctionName: 'hulu-catalog',
        InvocationType: 'Event',
        Payload: Buffer.from(
          JSON.stringify({ mod, band: i, scheduleNext }),
          'utf-8',
        ),
      })
      .promise();
  });

  return Promise.all(all);
};

export const scheduleFromS3 = async event => {
  return schedule(event);
};
