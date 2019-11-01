import { argv } from 'yargs';
import * as scrapers from './index';

const startScrape = async scraper => {
  let scraperToRun = scraper || process.env.SCRAPER;

  if (scrapers[scraperToRun]) {
    scrapers[scraperToRun](argv);
  } else {
    throw new Error(`Scraper \"${scraperToRun}\" not supported`);
  }
};

const run = async () => {
  await startScrape(process.argv[2]);
};

run();
