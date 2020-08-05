import redis from 'redis';
import {
  CloudWatchClient,
  PutMetricDataCommand,
} from '@aws-sdk/client-cloudwatch-node';
import { promisify } from 'util';

const cloudWatchClient = new CloudWatchClient({});

export async function handler() {
  const spiderKey = process.env.SPIDER_KEY!;

  const host = process.env.HOST || 'localhost';
  const client = redis.createClient({ host });

  const zcardAsync = promisify(client.zcard).bind(client);

  const card = await zcardAsync(`${spiderKey}:requests`);

  console.log(card);

  const command = new PutMetricDataCommand({
    MetricData: [
      {
        MetricName: 'outstanding_requests',
        Value: card,
        Timestamp: new Date(),
        Dimensions: [{ Name: 'spider', Value: spiderKey }],
      },
    ],
    Namespace: 'Teletracker/QA/Crawlers',
  });
  await cloudWatchClient.send(command);

  client.end(false);
}
