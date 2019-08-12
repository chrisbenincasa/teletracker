import * as scrapers from "./index";

const run = async () => {
  await scrapers[process.argv[2]]();
};

run();
