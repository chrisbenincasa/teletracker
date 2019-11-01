import { argv } from 'yargs';
import * as scrapers from './index';

const startScrape = async scraper => {
  let scraperToRun = scraper || process.env.SCRAPER;

  switch (scraperToRun) {
    case 'hboWhatsNew':
      await scrapers.hboWhatsNew(argv);
      break;

    case 'hboCatalog':
      await scrapers.hboCatalog(argv);
      break;

    case 'netflixOriginalsArriving':
      await scrapers.netflixOriginalsArriving(argv);
      break;

    case 'unogsNetflixExpiring':
      await scrapers.unogsNetflixExpiring(argv);
      break;

    case 'unogsNetflixAll':
      await scrapers.unogsNetflixAll(argv);
      break;

    case 'huluChanges':
      await scrapers.huluChanges(argv);
      break;

    case 'huluCatalog':
      await scrapers.huluCatalog(argv);
      break;

    case 'tmdbChanges':
      await scrapers.tmdbChanges(argv);
      break;

    case 'tmdbIds':
      await scrapers.tmdbIds(argv);
      break;

    default:
      throw new Error(`Scraper \"${scraperToRun}\" not supported`);
  }
};

const run = async () => {
  await startScrape(process.argv[2]);
  // await scrapers[process.argv[2]]();
};

run();
