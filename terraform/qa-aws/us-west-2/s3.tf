data "aws_s3_bucket" "teletracker-data-bucket" {
  provider = "aws.us-west-1"

  bucket = "teletracker-data"
}

resource "aws_s3_bucket" "teletracker-artifacts-us-west-2" {
  bucket = "us-west-2-teletracker-artifacts"
  region = "us-west-2"
}

resource "aws_s3_bucket" "teletracker-data-us-west-2" {
  bucket = "teletracker-data-us-west-2"
  region = "us-west-2"
}

resource "aws_s3_bucket_notification" "teletracker_data_bucket_notifications" {
  bucket = aws_s3_bucket.teletracker-data-us-west-2.id

  lambda_function {
    lambda_function_arn = module.hbo-catalog-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/hbo"
    filter_suffix       = "hbo-catalog-urls.txt"
  }

  lambda_function {
    lambda_function_arn = module.hulu-catalog-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/hulu"
    filter_suffix       = "hulu-catalog-urls.txt"
  }

  lambda_function {
    lambda_function_arn = module.netflix-originals-arriving.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/netflix/whats-on-netflix"
    filter_suffix       = "netflix-originals-catalog.json"
  }

  lambda_function {
    lambda_function_arn = module.tmdb-popularity-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/tmdb/movie_ids"
  }

  lambda_function {
    lambda_function_arn = module.tmdb-popularity-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/tmdb/tv_series_ids"
  }

  lambda_function {
    lambda_function_arn = module.tmdb-popularity-scheduler.lambda_arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "scrape-results/tmdb/person_ids"
  }
}
