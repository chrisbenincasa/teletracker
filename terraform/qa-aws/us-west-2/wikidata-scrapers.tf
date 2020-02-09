module "wikidata-dump" {
  source = "../scraper-lambda"

  function_name    = "wikidata-id-dump"
  handler_function = "index.wikibaseIdDump"

  create_default_trigger = false

  memory = 256

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

module "wikidata-data-dump" {
  source = "../scraper-lambda"

  function_name    = "wikidata-data-dump"
  handler_function = "index.wikibaseDataDump"

  create_default_trigger = false

  memory = 256

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}
