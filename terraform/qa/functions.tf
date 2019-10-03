module "hbo-scraper" {
  source = "./scraper"

  trigger_name     = "hbo-scrape-trigger"
  function_name    = "hbo-whats-new"
  entrypoint       = "hboWhatsNew"
  function_version = "1570050415"

  extra_env_vars = {
    JOB_CLASS_NAME = "com.teletracker.service.tools.IngestHboChanges"
    SCRAPER        = "hboWhatsNew"
  }
}

module "hbo-catalog-scraper" {
  source = "./scraper"

  trigger_name     = "hbo-catalog-scrape-trigger"
  function_name    = "hbo-catalog"
  entrypoint       = "hboCatalog"
  function_version = "1570050415"

  timeout = 300

  extra_env_vars = {
    SCRAPER = "hboCatalog"
  }
}

module "tmdb-ids" {
  source = "./scraper"

  trigger_name     = "tmdb-ids-scrape-trigger"
  function_name    = "tmdb-ids"
  entrypoint       = "tmdbIds"
  function_version = "1570138778"

  extra_env_vars = {
    SCRAPER = "tmdbIds"
  }

  event_data = {
    "movie" = jsonencode({
      "type" = "movie"
    }),
    "show" = jsonencode({
      "type" = "show"
    })
    "person" = jsonencode({
      "type" = "person"
    })
  }

  timeout   = 300
  memory_mb = 512

  cron_schedule = "30 8 * * *"
}

module "hulu-scraper" {
  source = "./scraper"

  trigger_name     = "hulu-scrape-trigger"
  function_name    = "hulu-changes"
  entrypoint       = "huluChanges"
  function_version = "1570050415"

  extra_env_vars = {
    JOB_CLASS_NAME = "com.teletracker.service.tools.IngestHuluChanges"
    SCRAPER        = "huluChanges"
  }
}

module "netflix-originals-arriving-scraper" {
  source = "./scraper"

  trigger_name     = "netflix-originals-arriving-scrape-trigger"
  function_name    = "netflix-originals-arriving"
  entrypoint       = "netflixOriginalsArriving"
  function_version = "1570050415"

  extra_env_vars = {
    JOB_CLASS_NAME = "com.teletracker.service.tools.IngestNetflixOriginalsArrivals"
    SCRAPER        = "netflixOriginalsArriving"
  }
}

module "unogs-netflix-expiring-scraper" {
  source = "./scraper"

  trigger_name     = "unogs-netflix-expiring-scrape-trigger"
  function_name    = "unogs-netflix-expiring"
  entrypoint       = "unogsNetflixExpiring"
  function_version = "1570050415"

  extra_env_vars = {
    JOB_CLASS_NAME = "com.teletracker.service.tools.IngestUnogsNetflixExpiring"
    SCRAPER        = "unogsNetflixExpiring"
  }
}

module "tmdb-changes-scraper" {
  source = "./scraper"

  trigger_name     = "tmdb-changes-scrape-trigger"
  function_name    = "tmdb-changes"
  entrypoint       = "tmdbChanges"
  function_version = "1570050415"

  extra_env_vars = {
    SCRAPER = "tmdbChanges"
  }
}

module "unogs-netflix-catalog-scraper" {
  source = "./scraper"

  trigger_name     = "unogs-netflix-catalog-scrape-trigger"
  function_name    = "unogs-netflix-catalog"
  entrypoint       = "unogsNetflixAll"
  function_version = "1570050415"
  timeout          = 120

  extra_env_vars = {
    SCRAPER = "unogsNetflixAll"
  }
}
