import request from 'request-promise';
import { DATA_BUCKET, USER_AGENT_STRING } from '../common/constants';
import _ from 'lodash';
import { getObjectS3, uploadToS3 } from '../common/storage';
import url from 'url';
import { createWriteStream } from '../common/stream_utils';
import { wait } from '../common/promise_utils';
import { isProduction } from '../common/env';
import moment from 'moment';
import fs from 'fs';
import readline from 'readline';

const TMDB_MOVIE_ID_PROP = 'P4947';
const TMDB_SERIES_ID_PROP = 'P4983';
const TMDB_PERSON_ID_PROP = 'P4985';
const IMDB_ID_PROP = 'P345';

const sanitizeProp = uri => uri.replace(/^https?:\/\/.*\/entity\//, '');

const makeQuery = (property, ids) => `SELECT ?item ?tmdb_id WHERE {
  ?item wdt:${property} ?tmdb_id;
  VALUES ?tmdb_id { ${ids.map(id => '"' + id + '"').join(' ')} }
}`;

const makeImdbQuery = (property, ids) => `SELECT ?item ?imdb_id WHERE {
  ?item wdt:${property} ?imdb_id;
  VALUES ?imdb_id { ${ids.map(id => '"' + id + '"').join(' ')} }
}`;

export const scrape = async event => {
  try {
    let ids = [];
    let type = event.type || 'movie';
    let prop;
    let propName = 'tmdb_id';
    let queryMaker = makeQuery;
    if (type === 'movie') {
      prop = TMDB_MOVIE_ID_PROP;
    } else if (type === 'tv' || type === 'show') {
      if (type === 'tv') {
        type = 'show';
      }
      prop = TMDB_SERIES_ID_PROP;
    } else if (type === 'person') {
      prop = TMDB_PERSON_ID_PROP;
    } else if (type === 'imdb_movie' || type === 'imdb_show') {
      prop = IMDB_ID_PROP;
      propName = 'imdb_id';
      queryMaker = makeImdbQuery;
    } else {
      console.error('Unrecognized type = ' + type);
      return;
    }

    if (event.file) {
      let offset = parseInt(event.offset) || 0;
      let limit = parseInt(event.limit) || 50;

      let parsed = url.parse(event.file);
      if (parsed.protocol.startsWith('s3')) {
        let pathname = parsed.pathname;
        if (pathname.charAt(0) === '/') {
          pathname = pathname.substring(1);
        }

        ids = await getObjectS3(parsed.host, pathname).then(body => {
          let all = body
            .toString('utf-8')
            .split('\n')
            .map(s => s.trim())
            .filter(s => s.length > 0)
            .map(line => JSON.parse(line).id);

          if (limit === -1) {
            return all.slice(offset, all.length);
          } else {
            return all.slice(offset, offset + limit);
          }
        });
      } else if (parsed.protocol.startsWith('file')) {
        let all = await new Promise((resolve, reject) => {
          let counter = 0;
          let lines = [];

          const readInterface = readline.createInterface({
            input: fs.createReadStream(parsed.pathname),
          });

          readInterface.on('line', line => {
            counter++;
            if (counter <= limit) {
              lines.push(line);
            } else {
              readInterface.close();
            }
          });

          readInterface.on('error', reject);

          readInterface.on('close', () => resolve(lines));
        });

        ids = all
          .map(s => s.trim())
          .filter(s => s.length > 0)
          .map(line => JSON.parse(line).id);
      }
    } else if (_.isArray(event.ids)) {
      ids = event.ids;
    } else if (_.isString(event.ids)) {
      ids = event.ids.split(',').filter(s => s.length > 0);
    }

    if (ids.length === 0) {
      return;
    }

    let minId = _.min(ids);
    let maxId = _.max(ids);
    let fileName = `${type}-${minId}_${maxId}-wikidata-mappings.json`;
    let [path, stream, flush] = createWriteStream(fileName);

    let all = _.chain(ids)
      .sort()
      .chunk(100)
      .reduce(async (prev, entries) => {
        await prev;

        let body = await request('https://query.wikidata.org/sparql', {
          headers: {
            'User-Agent': USER_AGENT_STRING,
            Accept: 'application/sparql-results+json',
          },
          json: true,
          qs: {
            query: queryMaker(prop, entries),
            format: 'json',
          },
        });

        let items = _.chain(body.results.bindings)
          .map(binding => {
            return {
              [propName]: binding[propName].value,
              id: sanitizeProp(binding.item.value),
            };
          })
          .sortBy(j => {
            let num = parseInt(j);
            return isNaN(num) ? j : num;
          })
          .value();

        items.forEach(item => {
          stream.write(JSON.stringify(item) + '\n');
        });

        return await wait(500);
      }, Promise.resolve())
      .value();

    await all;

    stream.close();

    await flush;

    if (isProduction()) {
      let formatted = moment().format('YYYY-MM-DD');
      await uploadToS3(
        DATA_BUCKET,
        `scrape-results/wikidata/${formatted}/${fileName}`,
        path,
      );
    }
  } catch (e) {
    console.error(e);
  }
};
