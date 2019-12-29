import * as AWS from 'aws-sdk';
import * as uuid from 'uuid/v4';

const getSqs = (() => {
  let sqs;
  return () => {
    if (!sqs) {
      sqs = new AWS.SQS();
    }

    return sqs;
  };
})();

export const scheduleTask = async payload => {
  let dedupid = uuid();
  console.log(
    `Scheduling task ${JSON.stringify(payload)}. Dedup ID ${dedupid}`,
  );
  return getSqs()
    .sendMessage({
      QueueUrl: process.env.TASK_QUEUE_URL,
      MessageBody: JSON.stringify(payload),
      MessageDeduplicationId: dedupid,
      MessageGroupId: 'default',
    })
    .promise();
};
