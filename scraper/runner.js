import { startScrape } from "./index";

const run = async () => {
  await startScrape(process.argv[2]);
  // await scrapers[process.argv[2]]();
};

run();
