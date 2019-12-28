import * as AWS from 'aws-sdk';

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
  console.log(`Scheduling task ${JSON.stringify(payload)}`);
  return getSqs()
    .sendMessage({
      QueueUrl: process.env.TASK_QUEUE_URL,
      MessageBody: JSON.stringify(payload),
    })
    .promise();
};
