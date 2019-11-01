import * as cheerio from 'cheerio';
import * as fs from 'fs';
import * as _ from 'lodash';
import moment from 'moment';
import request from 'request-promise';
import { uploadToStorage } from '../common/storage';
import { getFilePath } from '../common/tmp_files';
import { getObjectS3 } from '../common/storage';

const uaString =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36';
const movieRegex = /\/movies\/[A-z-]+\/?$/;
const showRegex = /https:\/\/www.hbo.com\/[A-z\-]+/;
const showFirstEpisodeRegex = /https:\/\/www.hbo.com\/[A-z\-]+\/season-0?1\/(episodes\/)?((episode-|chapter-|part-)?(1|01)(-[A-z0-9-]+)?|pilot)$/;

const wait = ms => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

const scrapeMovie = async url => {
  let key = 'scraping ' + url;

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

      if (movie) {
        console.timeEnd(key);
        let release = movie.dateCreated ? moment(movie.dateCreated) : null;
        return {
          name: movie.name,
          releaseYear: release ? release.year() : null,
          type: 'movie',
          network: 'HBO',
          nameFallback,
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

const scrapeTvShow = async firstEpisodeUrl => {
  let key = 'scraping ' + firstEpisodeUrl;
  console.time(key);
  let html = await request({
    uri: firstEpisodeUrl,
    headers: {
      'User-Agent': uaString,
    },
  });

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

        console.timeEnd(key);

        return {
          name: show.partOfSeason.partOfSeries.name,
          releaseYear: release ? release.year() : null,
          type: 'show',
          network: 'HBO',
        };
      }
    }
  }

  console.timeEnd(key);
};

const loadSitemapEntries = async date => {
  // let sitemap = await request({
  //   uri: `https://www.hbo.com/sitemap.xml`,
  //   headers: {
  //     'User-Agent': uaString,
  //   },
  // });
  //
  // let $ = cheerio.load(sitemap);
  //
  // return $('urlset > url > loc')
  //   .map((idx, el) => $(el).text())
  //   .get();
  return getObjectS3(
    'teletracker-data',
    `scrape-results/${date}/hbo-sitemap-urls.txt`,
  ).then(body => {
    return body.toString('utf-8').split('\n');
  });
};

const createWriteStream = fileName => {
  const stream = fs.createWriteStream(fileName, 'utf-8');
  return stream;
};

const scrape = async (event, context) => {
  console.log(event);

  if (!event.mod || !event.band) {
    console.error('requires mod and band specified');
    return;
  }

  let now = moment();
  let nowString = now.format('YYYY-MM-DD');

  console.time('loadSitemapEntries');
  let entries = await loadSitemapEntries(nowString);
  console.timeEnd('loadSitemapEntries');

  let fileName = nowString + '_hbo-catalog.' + event.band + '.json';
  let filePath = getFilePath(fileName);

  let stream = createWriteStream(filePath);

  let endMovies = _.chain(entries)
    .filter(entry => movieRegex.test(entry))
    .filter((_, idx) => idx % event.mod === event.band)
    .chunk(5)
    .reduce(async (prev, entries) => {
      await prev;

      let promises = _.map(entries, scrapeMovie);
      let res = await Promise.all(promises);
      if (res) {
        _.chain(res)
          .filter(_.negate(_.isUndefined))
          .each(r => stream.write(JSON.stringify(r) + '\n'))
          .value();
      }

      return wait(0);
    }, Promise.resolve())
    .value();

  await endMovies;

  let endShows = _.chain(entries)
    .filter(entry => showFirstEpisodeRegex.test(entry))
    .filter((_, idx) => idx % event.mod === event.band)
    .chunk(5)
    .reduce(async (prev, entries) => {
      await prev;

      let res = await Promise.all(_.map(entries, scrapeTvShow));

      if (res) {
        _.chain(res)
          .filter(_.negate(_.isUndefined))
          .each(r => stream.write(JSON.stringify(r) + '\n'))
          .value();
      }
      return wait(0);
    }, Promise.resolve())
    .value();

  await endShows;

  stream.close();

  let currentDate = moment().format('YYYY-MM-DD');
  if (process.env.NODE_ENV === 'production') {
    await uploadToStorage(fileName, 'scrape-results/' + currentDate);
  }
};

export { scrape };
