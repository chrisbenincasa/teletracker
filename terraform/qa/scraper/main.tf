locals {
  env_vars = {
    API_HOST          = "https://api.qa.teletracker.app"
    ADMINISTRATOR_KEY = "berglas://teletracker-secrets/administrator-key-qa"
    TMDB_API_KEY      = "berglas://teletracker-secrets/tmdb-api-key-qa"
  }
}

data "google_storage_bucket_object" "archive" {
  name   = "scrapers/scrapers-${var.function_version}.zip"
  bucket = "${var.bucket_name}"
}

resource "google_pubsub_topic" "scraper-trigger" {
  name = "${var.trigger_name}"
}

resource "google_cloudfunctions_function" "scraper-function" {
  name    = "${var.function_name}"
  runtime = "nodejs10"

  available_memory_mb   = "${var.memory_mb}"
  source_archive_bucket = "${var.bucket_name}"
  source_archive_object = "${data.google_storage_bucket_object.archive.name}"
  timeout               = "${var.timeout}"
  entry_point           = "${var.entrypoint}"

  event_trigger {
    event_type = "google.pubsub.topic.publish"
    resource   = "${google_pubsub_topic.scraper-trigger.name}"
  }

  environment_variables = {
    for key, value in merge(var.extra_env_vars, local.env_vars) :
    key => value
  }
}

resource "google_cloud_scheduler_job" "scraper-scheduler" {
  for_each = var.event_data

  name        = "${var.function_name}-scheduler-${each.key}"
  description = "Time-based scheduler for ${var.function_name}"
  schedule    = "${var.cron_schedule}"
  time_zone   = "${var.time_zone}"

  pubsub_target {
    topic_name = "${google_pubsub_topic.scraper-trigger.id}"
    data       = "${base64encode("${each.value}")}"
  }
}
