import { scrape as hboWhatsNew } from "./hbo/whats-new/scrape";
import { scrape as netflixOriginalsArriving } from "./netflix/scrape";
import { scrape as unogsNetflixExpiring } from "./unogs/scrape";
import { scrape as huluChanges } from "./hulu/changes/scrape";
import { substitute } from "./common/berglas";

const startScrape = async scraper => {
  await substitute();

  let scraperToRun = scraper || process.env.SCRAPER;

  switch (scraperToRun) {
    case "hboWhatsNew":
      await hboWhatsNew();
      break;

    case "netflixOriginalsArriving":
      await netflixOriginalsArriving();
      break;

    case "unogsNetflixExpiring":
      await unogsNetflixExpiring();
      break;

    case "huluChanges":
      await huluChanges();
      break;

    default:
      throw new Error(`Scraper \"${scraperToRun}\" not supported`);
  }
};

export {
  hboWhatsNew,
  netflixOriginalsArriving,
  unogsNetflixExpiring,
  huluChanges,
  startScrape
};
