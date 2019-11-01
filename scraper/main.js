import { scrape as hboCatalog } from './hbo/catalog';
import { scrape as hboWhatsNew } from './hbo/whats-new/scrape';
import { scrape as huluCatalog } from './hulu/catalog/scrape';
import { scrape as huluChanges } from './hulu/changes/scrape';
import { scrape as netflixOriginalsArriving } from './netflix/scrape';
import { scrape as tmdbChanges } from './tmdb/changes/scrape';
import { scrape as tmdbIds } from './tmdb/scrape-ids';
import { scrape as unogsNetflixExpiring } from './unogs/scrape';
import { scrape as unogsNetflixAll } from './unogs/scrape-all';

export {
  hboWhatsNew,
  hboCatalog,
  netflixOriginalsArriving,
  unogsNetflixExpiring,
  huluChanges,
  tmdbChanges,
  tmdbIds,
  unogsNetflixAll,
  huluCatalog,
};
