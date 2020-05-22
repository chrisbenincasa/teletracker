module "netflix-originals-arriving" {
  source = "../scraper-lambda"

  function_name    = "netflix-arriving-originals"
  handler_function = "index.netflixOriginalsArriving"

  create_default_trigger = false

  s3_bucket = var.scraper-s3-bucket

  runtime = "nodejs12.x"

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

resource "aws_lambda_permission" "netflix-originals-arriving-allow-teletracker-data" {
  action        = "lambda:InvokeFunction"
  function_name = module.netflix-originals-arriving.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.teletracker-data-us-west-2.arn
}