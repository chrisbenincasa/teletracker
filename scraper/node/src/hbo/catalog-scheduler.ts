import AWS from 'aws-sdk';
import _ from 'lodash';

export const schedule = async () => {
  const lambda = new AWS.Lambda({
    region: process.env.AWS_REGION,
  });

  let all = _.range(0, 16).map(async (i) => {
    await lambda
      .invoke({
        FunctionName: 'hbo-catalog',
        InvocationType: 'Event',
        Payload: Buffer.from(JSON.stringify({ mod: 16, band: i }), 'utf-8'),
      })
      .promise();
  });

  return Promise.all(all);
};

export const scheduleFromS3 = async () => {
  return schedule();
};
