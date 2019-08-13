module "hbo-scraper" {
  source = "./scraper"

  trigger_name     = "hbo-scrape-trigger"
  function_name    = "hbo-whats-new"
  entrypoint       = "hboWhatsNew"
  function_version = "1565625563"

  extra_env_vars = {
    JOB_CLASS_NAME = "com.teletracker.service.tools.IngestHboChanges"
    SCRAPER        = "hboWhtsNew"
  }
}

module "hulu-scraper" {
  source = "./scraper"

  trigger_name     = "hulu-scrape-trigger"
  function_name    = "hulu-changes"
  entrypoint       = "huluChanges"
  function_version = "1565627007"
}

module "netflix-originals-arriving-scraper" {
  source = "./scraper"

  trigger_name     = "netflix-originals-arriving-scrape-trigger"
  function_name    = "netflix-originals-arriving"
  entrypoint       = "netflixOriginalsArriving"
  function_version = "1565625563"
}

module "unogs-netflix-expiring-scraper" {
  source = "./scraper"

  trigger_name     = "unogs-netflix-expiring-scrape-trigger"
  function_name    = "unogs-netflix-expiring"
  entrypoint       = "unogsNetflixExpiring"
  function_version = "1565625563"
}
