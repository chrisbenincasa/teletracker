export interface BaseScrapeEvent {
  mod?: number;
  band?: number;
  offset?: number;
  limit?: number;
  scheduleNext?: boolean;
  parallelism?: number;
}
