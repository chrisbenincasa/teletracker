import * as cheerio from 'cheerio';
import * as _ from 'lodash';
import moment from 'moment';
import request from 'request-promise';
import { getObjectS3, uploadToS3 } from '../common/storage';
import { catalogSitemapS3Key } from './catalog-sitemap';
import { DATA_BUCKET } from '../common/constants';
import { isProduction } from '../common/env';
import { createWriteStream } from '../common/stream_utils';
import { sequentialPromises } from '../common/promise_utils';
import { resolveSecret } from '../common/aws_utils';
import striptags from 'striptags';
import { DEFAULT_BANDS, DEFAULT_PARALLELISM } from '../hulu/catalog/scheduler';

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';
const movieRegex = /\/movies\/[A-z-]+\/?$/;
const showRegex = /https:\/\/www.hbo.com\/[A-z\-]+/;
const showFirstEpisodeRegex = /https:\/\/www.hbo.com\/[A-z\-]+(\/season-0?1\/(episodes\/)?((episode-|chapter-|part-)?(1|01)(-[A-z0-9-]+)?|pilot))$/;

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const withRetries = async (tries, fn) => {
  const retryInner = async attempt => {
    try {
      return await fn();
    } catch (e) {
      console.error(e);
      if (attempt < tries) {
        await wait(1000);
        return retryInner(attempt + 1);
      } else {
        throw e;
      }
    }
  };

  return await retryInner(0);
};

const scrapeMovie = async url => {
  let key = 'scraping ' + url;

  console.time(key);
  let html;
  try {
    html = await withRetries(3, async () => {
      return await request({
        uri: url,
        headers: {
          'User-Agent': uaString,
        },
      });
    });
  } catch (e) {
    console.error('Could not scrape ' + url + ' after retrying');
    return;
  }

  let $ = cheerio.load(html);

  let pageState = $('noscript#react-data').attr('data-state');

  if (pageState) {
    let parsedPageState = JSON.parse(pageState);

    if (parsedPageState.pageSchemaList) {
      let movie = _.find(parsedPageState.pageSchemaList, schema => {
        return schema['@type'] === 'movie';
      });

      let nameFallback;
      let mainNav = _.find(
        parsedPageState.bands,
        band => band.band === 'MainNavigation',
      );
      if (mainNav && mainNav.data) {
        nameFallback = mainNav.data.subNavigationName;
      }

      if (!nameFallback) {
        if (parsedPageState.dataLayer && parsedPageState.dataLayer.pageInfo) {
          nameFallback = parsedPageState.dataLayer.pageInfo.nonSeriesTitle;
        }
      }

      let synopsisBand = _.find(parsedPageState.bands, { band: 'Text' });

      let description;
      if (synopsisBand && synopsisBand.data && synopsisBand.data.content) {
        description = striptags(synopsisBand.data.content)
          .replace(/&nbsp;/g, ' ')
          .trim();
      }

      let streamingId;
      let contentOverviewBand = _.find(parsedPageState.bands, {
        band: 'ContentOverview',
      });
      if (contentOverviewBand) {
        streamingId = _.get(
          contentOverviewBand,
          'data.infoSlice.streamingId.id',
        );
      }

      if (movie) {
        console.timeEnd(key);
        let release = movie.dateCreated ? moment(movie.dateCreated) : null;
        return {
          name: movie.name,
          releaseYear: release ? release.year() : null,
          type: 'movie',
          network: 'HBO',
          nameFallback,
          description,
          externalId: streamingId ? streamingId.toString() : undefined,
          originalUrl: pageState.canonicalUrl || url,
        };
      }
    }
  }

  console.timeEnd(key);
};

const fallbackTvShowReleaeDate = async firstEpisodeUrl => {
  let showUrl = showRegex.exec(firstEpisodeUrl);

  if (showUrl.length > 0) {
    let html = await request({
      uri: showUrl[0],
      headers: {
        'User-Agent': uaString,
      },
    });

    let $ = cheerio.load(html);

    let pageState = $('noscript#react-data').attr('data-state');

    if (pageState) {
      let parsedPageState = JSON.parse(pageState);
      let band = _.find(
        parsedPageState.bands,
        band => band.band === 'ContentOverview',
      );
      if (band) {
        let streamingId = _.at(band, 'data.infoSlice.streamingId.id');
        if (streamingId.length === 1) {
          streamingId = streamingId[0];
          let programsJson = await request({
            uri:
              'https://proxy-v4.cms.hbo.com/v1/schedule/programs?seriesIds=' +
              streamingId,
            json: true,
            headers: {
              'User-Agent': uaString,
            },
          });

          if (
            programsJson &&
            programsJson.programs &&
            programsJson.programs.length > 0
          ) {
            return moment(programsJson.programs[0].publishDate);
          }
        }
      }
    }
  } else {
    console.log('couldnt match ' + firstEpisodeUrl);
  }
};

const scrapeTvShowSynopsis = async url => {
  let key = 'scraping ' + url + ' for synopsis';
  console.time(key);
  let html = await request({
    uri: url,
    headers: {
      'User-Agent': uaString,
    },
  });

  let $ = cheerio.load(html);

  let pageState = $('noscript#react-data').attr('data-state');

  if (pageState) {
    let parsedPageState = JSON.parse(pageState);

    let synopsisBand = _.find(parsedPageState.bands, { band: 'Synopsis' });

    if (synopsisBand && synopsisBand.data && synopsisBand.data.summary) {
      console.timeEnd(key);
      return striptags(synopsisBand.data.summary)
        .replace(/&nbsp;/g, ' ')
        .trim();
    }
  }
  console.timeEnd(key);
};

const scrapeTvShow = async firstEpisodeUrl => {
  let key = 'scraping ' + firstEpisodeUrl;
  console.time(key);
  let html;
  try {
    html = await withRetries(3, async () => {
      return await request({
        uri: firstEpisodeUrl,
        headers: {
          'User-Agent': uaString,
        },
      });
    });
  } catch (e) {
    console.error('Could not scrape ' + url + ' after retrying');
    return;
  }

  let result = showFirstEpisodeRegex.exec(firstEpisodeUrl);
  let baseUrl = firstEpisodeUrl.replace(result[1], '');

  let $ = cheerio.load(html);

  let pageState = $('noscript#react-data').attr('data-state');

  if (pageState) {
    let parsedPageState = JSON.parse(pageState);

    if (parsedPageState.pageSchemaList) {
      let show = _.find(parsedPageState.pageSchemaList, schema => {
        return schema && schema['@type'] === 'TVEpisode';
      });

      if (show) {
        let release =
          show.releasedEvent && show.releasedEvent.startDate
            ? moment(show.releasedEvent.startDate)
            : null;

        if (!release) {
          let videoObject = _.find(parsedPageState.pageSchemaList, schema => {
            return schema && schema['@type'] === 'VideoObject';
          });

          if (videoObject && videoObject.dateCreated) {
            release = moment(videoObject.dateCreated, 'MM-DD-YYYY');
          }
        }

        if (!release) {
          release = await fallbackTvShowReleaeDate(firstEpisodeUrl);
        }

        let streamingId;
        let contentOverviewBand = _.find(parsedPageState.bands, {
          band: 'ContentOverview',
        });
        if (contentOverviewBand) {
          streamingId = _.get(
            contentOverviewBand,
            'data.infoSlice.streamingId.id',
          );
        }

        console.timeEnd(key);

        return {
          name: show.partOfSeason.partOfSeries.name,
          releaseYear: release ? release.year() : null,
          type: 'show',
          network: 'HBO',
          description: await scrapeTvShowSynopsis(baseUrl),
          externalId: streamingId ? streamingId.toString() : undefined,
          originalUrl: baseUrl,
        };
      }
    }
  }

  console.timeEnd(key);
};

const loadSitemapEntries = async date => {
  return getObjectS3(DATA_BUCKET, catalogSitemapS3Key(date)).then(body => {
    return body.toString('utf-8').split('\n');
  });
};

const scrape = async (event, context) => {
  console.log(event);

  let band =
    !_.isUndefined(event.band) && !_.isNaN(Number(event.band))
      ? Number(event.band)
      : undefined;
  let mod =
    (!_.isUndefined(event.mod) && !_.isNaN(Number(event.mod))
      ? Number(event.mod)
      : undefined) || DEFAULT_BANDS;

  let offset = event.offset || 0;
  let limit = event.limit || -1;

  // if (_.isUndefined(event.mod)  _.isUndefined(event.band)) {
  //   console.error('requires mod and band specified');
  //   return;
  // }

  let now = moment();
  let nowString = now.format('YYYY-MM-DD');

  console.time('loadSitemapEntries');
  let entries = await loadSitemapEntries(nowString);
  console.timeEnd('loadSitemapEntries');

  let fileName = nowString + '_hbo-catalog' + '.json';
  if (!_.isUndefined(mod) && !_.isUndefined(band)) {
    fileName = `${nowString}_hbo-catalog.${band}.json`;
  }

  let [path, stream, flush] = createWriteStream(fileName);

  let filteredEntries = entries
    .filter(
      entry => movieRegex.test(entry) || showFirstEpisodeRegex.test(entry),
    )
    .filter((item, idx) => {
      if (!_.isUndefined(mod) && !_.isUndefined(band)) {
        return idx % mod === band;
      } else {
        return true;
      }
    });

  filteredEntries = filteredEntries.slice(
    offset,
    limit === -1 ? filteredEntries.length : offset + limit,
  );

  console.log('Filtered to ' + filteredEntries.length + ' urls');

  await sequentialPromises(
    _.chain(filteredEntries)
      .filter(entry => movieRegex.test(entry))
      .chunk(5)
      .value(),
    Number(event.perBatchWait) || 0,
    async entries => {
      let promises = _.map(entries, scrapeMovie);
      let res = await Promise.all(promises);
      if (res) {
        _.chain(res)
          .filter(_.negate(_.isUndefined))
          .each(r => stream.write(JSON.stringify(r) + '\n'))
          .value();
      }
    },
  );

  await sequentialPromises(
    _.chain(filteredEntries)
      .filter(entry => showFirstEpisodeRegex.test(entry))
      .chunk(5)
      .value(),
    Number(event.perBatchWait) || 0,
    async entries => {
      let res = await Promise.all(_.map(entries, scrapeTvShow));
      if (res) {
        _.chain(res)
          .filter(_.negate(_.isUndefined))
          .each(r => stream.write(JSON.stringify(r) + '\n'))
          .value();
      }
    },
  );

  stream.close();

  await flush;

  let currentDate = moment().format('YYYY-MM-DD');
  if (isProduction()) {
    await uploadToS3(
      DATA_BUCKET,
      `scrape-results/hbo/${currentDate}/full_catalog/${fileName}`,
      path,
    );
  }
};

export const scrapeAll = async () => {
  try {
    await sequentialPromises(_.range(0, 16), 5000, async i => {
      await scrape({ mod: 16, band: i });
    });
  } catch (e) {
    console.error(e);
  }
};

export const scrapeSingle = async event => {
  try {
    let url = event.url;

    if (movieRegex.test(url)) {
      console.log(JSON.stringify(await scrapeMovie(url), null, 2));
    } else if (showFirstEpisodeRegex.test(url)) {
      console.log(JSON.stringify(await scrapeTvShow(url), null, 2));
    }
    try {
    } catch (e) {
      console.error(e);
    }
  } catch (e) {
    console.error(e);
  }
};

export { scrape };
