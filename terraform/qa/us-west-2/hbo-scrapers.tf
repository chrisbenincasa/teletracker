module "hbo-whats-new-scraper" {
  source = "../scraper-lambda"

  handler_function = "index.hboWhatsNew"
  function_name    = "hbo-whats-new"

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}