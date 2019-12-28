import { Client } from '@elastic/elasticsearch';
import AWS from 'aws-sdk';

const getSsm = (() => {
  let ssm;
  return () => {
    if (!ssm) {
      ssm = new AWS.SSM();
    }

    return ssm;
  };
})();

const resolveSecret = (() => {
  let cachedSecrets = {};

  return async secret => {
    if (!cachedSecrets[secret]) {
      console.log('Could not find cached secret, hitting SSM API');
      cachedSecrets[secret] = await getSsm()
        .getParameter({
          Name: secret,
          WithDecryption: true,
        })
        .promise()
        .then(param => param.Parameter.Value);
    }

    return cachedSecrets[secret];
  };
})();

const getClient = (() => {
  let client;
  return async () => {
    if (!client) {
      console.log('Could not find cached ES client.');

      client = new Client({
        node: `https://${process.env.ES_HOST}`,
        ssl: {
          host: process.env.ES_HOST,
        },
        auth: {
          username: process.env.ES_USERNAME,
          password: await resolveSecret(process.env.ES_PASSWORD),
        },
      });
    }

    return client;
  };
})();

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

export const handler = async event => {
  let client = await getClient();

  if (event.Records) {
    let all = event.Records.reduce(async (prev, record) => {
      let last = await prev;

      let jsonRecord = JSON.parse(record.body);

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
