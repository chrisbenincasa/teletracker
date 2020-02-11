module "hbo-whats-new-scraper" {
  source = "../scraper-lambda"

  handler_function = "index.hboWhatsNew"
  function_name    = "hbo-whats-new"

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

module "hbo-catalog-scraper" {
  source = "../scraper-lambda"

  handler_function = "index.hboCatalog"
  function_name    = "hbo-catalog"

  memory = 256

  create_default_trigger = false

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

module "hbo-catalog-dump" {
  source = "../scraper-lambda"

  handler_function = "index.hboCatalogDump"
  function_name    = "hbo-catalog-dump"

  memory = 256

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

module "hbo-catalog-scheduler" {
  source = "../scraper-lambda"

  handler_function = "index.scheduleHboCatalogS3"
  function_name    = "hbo-catalog-scheduler"

  create_default_trigger = false

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

resource "aws_lambda_permission" "hbo-catalog-dump-allow-teletracker-data" {
  action        = "lambda:InvokeFunction"
  function_name = module.hbo-catalog-scheduler.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.teletracker-data-us-west-2.arn
}

resource "aws_iam_role_policy_attachment" "hbo-catalog-scheduler-lambda-invoke" {
  policy_arn = aws_iam_policy.lambda_execute.arn
  role       = module.hbo-catalog-scheduler.lambda_role_name
}

//resource "aws_cloudwatch_event_rule" "scraper_lambda_event_rule" {
//  count               = 4
//  name                = "${module.hbo-catalog-scraper.lambda_name}-trigger-band-${count.index}"
//  description         = "Run ${module.hbo-catalog-scraper.lambda_name}"
//  schedule_expression = "cron(${count.index * 5} 5 * * ? *)"
//}
//
//resource "aws_cloudwatch_event_target" "scraper_lambda_event_target" {
//  count = 16
//
//  arn = module.hbo-catalog-scraper.lambda_arn
//  input = jsonencode({
//    "mod"  = 16,
//    "band" = count.index
//  })
//  rule = element(aws_cloudwatch_event_rule.scraper_lambda_event_rule, count.index % 4).name
//}
