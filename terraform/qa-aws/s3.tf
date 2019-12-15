data "aws_s3_bucket" "teletracker-data-bucket" {
  bucket = "teletracker-data"
}

resource "aws_s3_bucket" "teletracker-artifacts-us-west-2" {
  provider = "aws.us-west-2"

  bucket = "us-west-2-teletracker-artifacts"
  region = "us-west-2"
}

resource "aws_s3_bucket_notification" "hbo-catalog-dump-pushed" {
  bucket = data.aws_s3_bucket.teletracker-data-bucket.id

  lambda_function {
    lambda_function_arn = module.hbo-catalog-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results"
    filter_suffix       = "hbo-catalog-urls.txt"
  }

  lambda_function {
    lambda_function_arn = module.hulu-catalog-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results"
    filter_suffix       = "hulu-catalog-urls.txt"
  }

  lambda_function {
    lambda_function_arn = module.netflix-originals-arriving.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/netflix/whats-on-netflix"
    filter_suffix       = "netflix-originals-catalog.json"
  }

  lambda_function {
    events        = ["s3:ObjectCreated:*"]
    filter_prefix = "scrape-results/tmdb"
  }
}