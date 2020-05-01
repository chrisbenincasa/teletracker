import * as AWS from 'aws-sdk';
import { v4 as uuidv4 } from 'uuid';

const getSqs = (() => {
  let sqs: AWS.SQS;
  return () => {
    if (!sqs) {
      sqs = new AWS.SQS();
    }

    return sqs;
  };
})();

export const scheduleTask = async (payload: any) => {
  let dedupid = uuidv4();
  let jsonPayload = JSON.stringify(payload);
  console.log(`Scheduling task ${jsonPayload}. Dedup ID ${dedupid}`);
  return getSqs()
    .sendMessage({
      QueueUrl: process.env.TASK_QUEUE_URL!,
      MessageBody: jsonPayload,
      MessageDeduplicationId: dedupid,
      MessageGroupId: 'default',
    })
    .promise();
};
