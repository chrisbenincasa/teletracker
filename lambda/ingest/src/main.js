import { Client } from '@elastic/elasticsearch';
import AWS from 'aws-sdk';
import AwsConnection from './es-connection';

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
        Connection: AwsConnection,
        node: `https://${process.env.ES_HOST}`,
        ssl: {
          host: process.env.ES_HOST,
        },
        // auth: {
        //   username: process.env.ES_USERNAME,
        //   password: await resolveSecret(process.env.ES_PASSWORD),
        // },
      });
    }

    return client;
  };
})();

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const withTryCatch = async f => {
  try {
    let response = await f();
    if (process.env.SLEEP && process.env.SLEEP > 0) {
      await wait(Number(process.env.SLEEP));
    }
    return response;
  } catch (e) {
    console.error(e);
    throw e;
  }
};

const handleRecord = async (client, jsonRecord) => {
  switch (jsonRecord.operation) {
    case 'index':
      return await withTryCatch(() => {
        return client.index({
          id: jsonRecord.index.id,
          index: jsonRecord.index.index,
          body: jsonRecord.index.body,
        });
      });
    case 'update':
      return await withTryCatch(() => {
        return client.update({
          index: jsonRecord.update.index,
          id: jsonRecord.update.id,
          body: {
            script: jsonRecord.update.script
              ? jsonRecord.update.script
              : undefined,
            doc: jsonRecord.update.doc ? jsonRecord.update.doc : undefined,
          },
        });
      });

    default:
      console.error(`Op type ${jsonRecord.operation} is unsupported`);
      return;
  }
};

export const handler = async event => {
  let client = await getClient();

  if (event.Records) {
    let all = await event.Records.reduce(async (prev, record) => {
      let last = await prev;

      let jsonRecord = JSON.parse(record.body);

      let response = await handleRecord(client, jsonRecord);

      if (response) {
        return [...last, response];
      } else {
        return last;
      }
    }, Promise.resolve([]));

    return Promise.all(all);
  }
};

export const handleDirect = async event => {
  let client = await getClient();

  try {
    return await handleRecord(client, JSON.parse(event.payload));
  } catch (e) {
    throw new Error(e);
  }
};
