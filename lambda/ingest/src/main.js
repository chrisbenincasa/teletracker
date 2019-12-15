import { Client } from '@elastic/elasticsearch';

const client = new Client({
  node: `https://${process.env.ES_HOST}`,
  ssl: {
    host: process.env.ES_HOST,
  },
  auth: {
    username: process.env.ES_USERNAME,
    password: process.env.ES_PASSWORD,
  },
});

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

export const handler = async event => {
  console.log(event);
  if (event.Records) {
    let all = event.Records.reduce(async (prev, record) => {
      let last = await prev;

      let jsonRecord = JSON.parse(record.body);

      console.log(jsonRecord);

      switch (jsonRecord.operation) {
        case 'update':
          let response = await client.update({
            index: jsonRecord.update.index,
            id: jsonRecord.update.id,
            body: {
              script: jsonRecord.update.script
                ? jsonRecord.update.script
                : undefined,
              doc: jsonRecord.update.doc ? jsonRecord.update.doc : undefined,
            },
          });

          if (process.env.SLEEP && process.env.SLEEP > 0) {
            await wait(Number(process.env.SLEEP));
          }

          return [...last, response];

        default:
          console.error(`Op type ${jsonRecord.operation} is unsupported`);
          return last;
      }
    });

    return Promise.all(all);
  }
};
