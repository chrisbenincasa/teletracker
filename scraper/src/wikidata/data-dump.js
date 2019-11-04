import request from 'request-promise';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import _ from 'lodash';
import { getDirectoryS3, getObjectS3, uploadToS3 } from '../common/storage';
import url from 'url';
import { createWriteStream } from '../common/stream_utils';
import { isProduction } from '../common/env';
import { wait } from '../common/promise_utils';

const wbk = require('wikibase-sdk')({
  instance: 'https://www.wikidata.org',
  sparqlEndpoint: 'https://query.wikidata.org/sparql',
});

const getAndProcessObject = async (bucket, object, type, offset, limit) => {
  console.log(`Preparing to process: s3://${bucket}/${object.Key}`);
  console.time(object.Key);

  let objs = await getObjectS3(bucket, object.Key).then(body => {
    let lines = body.toString('utf-8').split('\n');
    return _.chain(lines)
      .map(s => s.trim())
      .filter(s => s.length > 0)
      .map(line => JSON.parse(line))
      .sortBy(i => Number(i.tmdb_id))
      .slice(offset, limit === -1 ? undefined : offset + limit)
      .value();
  });

  let byId = _.chain(objs)
    .groupBy('id')
    .mapValues(_.head)
    .value();

  let wikiIds = _.map(objs, 'id');

  let urls = wbk.getManyEntities({ ids: wikiIds });

  let minId = _.minBy(objs, o => Number(o.tmdb_id)).tmdb_id;
  let maxId = _.maxBy(objs, o => Number(o.tmdb_id)).tmdb_id;
  let fileName = `${type}-${minId}_${maxId}-wikidata-dump.json`;
  let [path, stream, flush] = createWriteStream(fileName);

  for await (let url of urls) {
    try {
      let body = await request(url, {
        headers: {
          'User-Agent': USER_AGENT_STRING,
        },
        json: true,
      });

      console.log('Finished pull of 50 IDs');

      Object.keys(body.entities)
        .map(id => {
          return {
            tmdb_id: byId[id].tmdb_id,
            entity: body.entities[id],
          };
        })
        .forEach(obj => stream.write(JSON.stringify(obj) + '\n'));

      await wait(1000);
    } catch (e) {
      console.error(e);
      throw e;
    }
  }

  stream.close();
  await flush;

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `data-dump/wikidata/${type}/${fileName}`,
      path,
      'text/plain',
      true,
    );
  }

  console.timeEnd(object.Key);
};

export const dataDump = async event => {
  let type = event.type || 'movie';

  if (event.file) {
    let offset = parseInt(event.offset) || 0;
    let limit = parseInt(event.limit) || 50;

    let parsed = url.parse(event.file);
    if (parsed.protocol.startsWith('s3')) {
      let pathname = parsed.pathname;
      if (pathname.charAt(0) === '/') {
        pathname = pathname.substring(1);
      }

      if (event.directory) {
        let objects = await getDirectoryS3(parsed.host, pathname);
        for await (let object of objects.slice(
          offset,
          limit === -1 ? objects.length : offset + limit,
        )) {
          try {
            await getAndProcessObject(parsed.host, object, type, 0, -1);
          } catch (e) {
            console.error(e);
          }
        }
      } else {
        await getAndProcessObject(parsed.host, pathname, type, offset, limit);
      }
    }
  }
};
