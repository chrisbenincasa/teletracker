import { argv } from 'yargs';
import * as hooks from './index';

const startScrape = async hook => {
  let hookToRun = hook || process.env.HOOK;

  if (hooks[hookToRun]) {
    hooks[hookToRun](argv);
  } else {
    throw new Error(`Scraper \"${hookToRun}\" not supported`);
  }
};

const run = async () => {
  try {
    await startScrape(process.argv[2]);
  } catch (e) {
    console.error(e);
  }
};

run();
