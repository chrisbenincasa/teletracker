module "whats-on-netflix-catalog" {
  source = "./scraper-lambda"

  function_name    = "whats-on-netflix-catalog"
  handler_function = "index.whatsOnNetflixCatalog"
}

module "netflix-originals-arriving" {
  source = "./scraper-lambda"

  function_name    = "netflix-arriving-originals"
  handler_function = "index.netflixOriginalsArriving"

  create_default_trigger = false
}

resource "aws_lambda_permission" "netflix-originals-arriving-allow-teletracker-data" {
  action        = "lambda:InvokeFunction"
  function_name = module.netflix-originals-arriving.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = data.aws_s3_bucket.teletracker-data-bucket.arn
}