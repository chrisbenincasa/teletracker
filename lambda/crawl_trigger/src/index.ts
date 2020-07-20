import { GetRecordsOutput } from '@aws-sdk/client-dynamodb-streams-node';
import mappings from './job_mappings';
import { SQSClient, SendMessageCommand } from '@aws-sdk/client-sqs-node';
import { v4 as uuidv4 } from 'uuid';

type TaskMessage = {
  id: string;
  clazz: string;
  args: object;
  jobTags?: string[];
};

const sqsClient = new SQSClient({});

async function asyncForEach<T>(
  array: T[],
  callback: (x: T, index: number, array: T[]) => void,
) {
  for (let index = 0; index < array.length; index++) {
    await callback(array[index], index, array);
  }
}

const scheduleTask = async (payload: TaskMessage) => {
  let dedupid = uuidv4();
  let jsonPayload = JSON.stringify(payload);
  console.log(`Scheduling task ${jsonPayload}. Dedup ID ${dedupid}`);
  const command = new SendMessageCommand({
    QueueUrl: process.env.TASK_QUEUE_URL as string,
    MessageBody: jsonPayload,
    MessageDeduplicationId: dedupid,
    MessageGroupId: payload.clazz,
  });

  return sqsClient.send(command);
};

export async function handler(event: GetRecordsOutput) {
  await asyncForEach(event.Records || [], async (record) => {
    if (record.eventName === 'MODIFY') {
      if (
        record.dynamodb?.NewImage?.time_closed &&
        !record.dynamodb?.OldImage?.time_closed
      ) {
        const crawlerName = record.dynamodb?.Keys?.spider.S;
        if (crawlerName && mappings[crawlerName]) {
          console.log(
            `Finished crawling ${crawlerName}. Scheduling import job.`,
          );
          try {
            await scheduleTask({
              id: uuidv4(),
              clazz: mappings[crawlerName].jobClass,
              args: {},
            });
          } catch (e) {
            console.error(e);
          }
        } else {
          console.log(`Unrecoginized crawler type: ${crawlerName}`);
        }
      }

      console.log('finished crawl: ' + JSON.stringify(record.dynamodb));
    } else if (record.eventName === 'INSERT') {
      console.log('started new crawl: ' + JSON.stringify(record.dynamodb));
    }
  });
}
