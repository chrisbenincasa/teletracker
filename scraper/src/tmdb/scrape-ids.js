import { spawn } from 'child_process';
import fs from 'fs';
import moment from 'moment';
import request from 'request';
import split2 from 'split2';
import transform from 'stream-transform';
import zlib from 'zlib';
import { uploadToStorage } from '../common/storage';

function streamToPromise(stream) {
  return new Promise(function(resolve, reject) {
    stream.on('close', () => {
      console.log('stream is over..');
      resolve();
    });
    stream.on('error', e => reject(e));
  });
}

const isProduction = () => process.env.NODE_ENV === 'production';

const getFileName = name => {
  if (isProduction()) {
    return '/tmp/' + name;
  } else {
    return name;
  }
};

const scrapeTypeIds = async (typePrefix, todayMoment, toTsv, fromTsv) => {
  let underscoreFmt = todayMoment.format('MM_DD_YYYY');

  let outTsv = getFileName(`${typePrefix}_ids-${underscoreFmt}.tsv`);
  let tmp = fs.createWriteStream(outTsv);

  let uri = `http://files.tmdb.org/p/exports/${typePrefix}_ids_${underscoreFmt}.json.gz`;

  let s = request(uri, {
    gzip: true,
    headers: { 'Accept-Encoding': 'gzip, deflate' },
  })
    .pipe(zlib.createGunzip())
    .pipe(split2())
    .pipe(
      transform(line => {
        return toTsv(line) + '\n';
      }),
    )
    .pipe(tmp);

  await streamToPromise(s);

  let sortedFileName = getFileName(
    `${todayMoment.format('YYYY-MM-DD')}_${typePrefix}-ids-sorted.json`,
  );
  let outStream = fs.createWriteStream(sortedFileName, {
    encoding: 'utf8',
  });

  console.log('finished downloading, spawning sort');

  let sortStream = spawn('sort', [
    '-k3,3',
    '--field-separator=\t',
    '-n',
    '-r',
    outTsv,
  ]);

  sortStream.stdout
    .pipe(split2())
    .pipe(
      transform(line => {
        return fromTsv(line) + '\n';
      }),
    )
    .pipe(outStream);

  sortStream.stderr.on('data', data => {
    console.error(data.toString('utf8'));
  });

  sortStream.on('close', code => {
    if (code) {
      console.error('exited with code ' + code);
    }

    console.log('Done');
  });

  await streamToPromise(sortStream);

  return sortedFileName;
};

const movieToTsv = line => {
  let parsed = JSON.parse(line);
  return [
    parsed.id,
    (parsed.original_title || '').replace('\t', ' '),
    parsed.popularity,
    parsed.video,
    parsed.adult,
  ].join('\t');
};

const movieFromTsv = line => {
  let parts = line.split('\t');
  return JSON.stringify(
    {
      id: parseInt(parts[0]),
      original_title: parts[1],
      popularity: parseFloat(parts[2]),
      video: parts[3] === 'true',
      adult: parts[4] === 'true',
    },
    undefined,
    0,
  );
};

const tvShowToTsv = line => {
  let parsed = JSON.parse(line);
  return [
    parsed.id,
    (parsed.original_name || '').replace('\t', ' '),
    parsed.popularity,
  ].join('\t');
};

const tvShowFromTsv = line => {
  let parts = line.split('\t');
  return JSON.stringify(
    {
      id: parseInt(parts[0]),
      original_name: parts[1],
      popularity: parseFloat(parts[2]),
    },
    undefined,
    0,
  );
};

const personToTsv = line => {
  let parsed = JSON.parse(line);
  return [
    parsed.id,
    (parsed.original_name || '').replace('\t', ' '),
    parsed.popularity,
    parsed.adult,
  ].join('\t');
};

const personFromTsv = line => {
  let parts = line.split('\t');
  return JSON.stringify(
    {
      id: parseInt(parts[0]),
      original_name: parts[1],
      popularity: parseFloat(parts[2]),
      adult: parts[3] === 'true',
    },
    undefined,
    0,
  );
};

const scrape = async (event, context) => {
  const payload =
    event && event.data
      ? JSON.parse(Buffer.from(event.data, 'base64').toString())
      : {};

  let now = payload.date ? moment(payload.date, 'YYYY-MM-DD') : moment();
  let currentDate = moment().format('YYYY-MM-DD');

  if (!payload.type) {
    payload.type = 'all';
  }

  let movieUpload;
  if (payload.type === 'movie' || payload.type === 'all') {
    try {
      let movieFile = await scrapeTypeIds(
        'movie',
        now,
        movieToTsv,
        movieFromTsv,
      );

      if (isProduction()) {
        movieUpload = uploadToStorage(
          movieFile,
          'scrape-results/' + currentDate,
        );
      }
    } catch (e) {
      console.error(e);
    }
  }

  let tvUpload;
  if (payload.type === 'show' || payload.type === 'all') {
    try {
      let tvFile = await scrapeTypeIds(
        'tv_series',
        now,
        tvShowToTsv,
        tvShowFromTsv,
      );

      if (isProduction()) {
        tvUpload = uploadToStorage(tvFile, 'scrape-results/' + currentDate);
      }
    } catch (e) {
      console.error(e);
    }
  }

  let personUpload;
  if (payload.type === 'person' || payload.type === 'all') {
    try {
      let personFile = await scrapeTypeIds(
        'person',
        now,
        personToTsv,
        personFromTsv,
      );

      if (isProduction()) {
        personUpload = uploadToStorage(
          personFile,
          'scrape-results/' + currentDate,
        );
      }
    } catch (e) {
      console.error(e);
    }
  }

  if (process.env.NODE_ENV === 'production') {
    if (movieUpload) await movieUpload;
    if (tvUpload) await tvUpload;
    if (personUpload) await personUpload;
  }
};

export { scrape };
