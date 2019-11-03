import { spawn } from 'child_process';
import moment from 'moment';
import request from 'request';
import split2 from 'split2';
import transform from 'stream-transform';
import zlib from 'zlib';
import { uploadToS3 } from '../common/storage';
import { createWriteStream } from '../common/stream_utils';
import { DATA_BUCKET } from '../common/constants';
import { getFilePath } from '../common/tmp_files';

function streamToPromise(stream) {
  return new Promise(function(resolve, reject) {
    stream.on('close', resolve);
    stream.on('error', e => reject(e));
  });
}

const isProduction = () => process.env.NODE_ENV === 'production';

const scrapeTypeIds = async (typePrefix, todayMoment, toTsv, fromTsv) => {
  let currentDate = moment().format('YYYY-MM-DD');
  let underscoreFmt = todayMoment.format('MM_DD_YYYY');

  let uri = `http://files.tmdb.org/p/exports/${typePrefix}_ids_${underscoreFmt}.json.gz`;

  let sortStream = spawn(
    'sort',
    ['-k3,3', '--field-separator=\t', '-n', '-r'],
    {
      stdio: ['pipe', 'pipe', 'pipe'],
    },
  );

  request(uri, {
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
    .pipe(sortStream.stdin);

  let sortedFileName = `${todayMoment.format(
    'YYYY-MM-DD',
  )}_${typePrefix}-ids-sorted.json`;

  let [_1, outStream, _2] = createWriteStream(sortedFileName);

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
  });

  await streamToPromise(sortStream);

  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/${currentDate}/${sortedFileName}.gz`,
      getFilePath(sortedFileName),
      'text/plain',
      true,
    );
  }
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
  let now = event.date ? moment(event.date, 'YYYY-MM-DD') : moment();
  let currentDate = moment().format('YYYY-MM-DD');

  if (!event.type) {
    event.type = 'all';
  }

  let movieUpload;
  if (event.type === 'movie' || event.type === 'all') {
    try {
      movieUpload = scrapeTypeIds('movie', now, movieToTsv, movieFromTsv);
    } catch (e) {
      console.error(e);
    }
  }

  let tvUpload;
  if (event.type === 'show' || event.type === 'all') {
    try {
      tvUpload = scrapeTypeIds('tv_series', now, tvShowToTsv, tvShowFromTsv);
    } catch (e) {
      console.error(e);
    }
  }

  let personUpload;
  if (event.type === 'person' || event.type === 'all') {
    try {
      personUpload = scrapeTypeIds('person', now, personToTsv, personFromTsv);
    } catch (e) {
      console.error(e);
    }
  }

  if (isProduction()) {
    if (movieUpload) await movieUpload;
    if (tvUpload) await tvUpload;
    if (personUpload) await personUpload;
  }
};

export { scrape };
