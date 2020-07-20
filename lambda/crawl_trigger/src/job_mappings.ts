export type Mappings = {
  [k: string]: {
    jobClass: string;
  };
};

export enum CrawlerName {
  hbo_changes,
}

const mappings: Mappings = {
  hbo_changes: {
    jobClass: 'com.teletracker.tasks.scraper.hbo.IngestHboChangesDelta',
  },
};

export default mappings;
