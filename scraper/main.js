import { scrape as hboWhatsNew } from "./hbo/whats-new/scrape";
import { scrape as netflixOriginalsArriving } from "./netflix/scrape";
import { scrape as unogsNetflixExpiring } from "./unogs/scrape";
import { scrape as unogsNetflixAll } from "./unogs/scrape-all";
import { scrape as huluChanges } from "./hulu/changes/scrape";
import { scrape as tmdbChanges } from "./tmdb/changes/scrape";
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

    case "unogsNetflixAll":
      await unogsNetflixAll();
      break;

    case "huluChanges":
      await huluChanges();
      break;

    case "tmdbChanges":
      await tmdbChanges();
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
  tmdbChanges,
  startScrape
};
