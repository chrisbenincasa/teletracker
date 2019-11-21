module "whats-on-netflix-catalog" {
  source = "./scraper-lambda"

  function_name    = "whats-on-netflix-catalog"
  handler_function = "index.whatsOnNetflixCatalog"
}

module "new-on-netflix-catalog" {
  source = "./scraper-lambda"

  function_name    = "new-on-netflix-catalog"
  handler_function = "index.newOnNetflixCatalog"

  memory = 256

  create_default_trigger = false
}

resource "aws_cloudwatch_event_rule" "new-on-netflix-event-rule" {
  name                = "${module.new-on-netflix-catalog.lambda_name}-trigger"
  description         = "Run ${module.new-on-netflix-catalog.lambda_name}"
  schedule_expression = "cron(0 8 * * ? *)"
}

resource "aws_cloudwatch_event_target" "new-on-netflix-event-target" {
  arn = module.new-on-netflix-catalog.lambda_arn
  input = jsonencode({
    "letter" = "all",
    "limit"  = 4
  })
  rule = aws_cloudwatch_event_rule.new-on-netflix-event-rule.name
}

resource "aws_iam_role_policy_attachment" "new-on-netflix-catalog-lambda-invoke" {
  policy_arn = aws_iam_policy.lambda_execute.arn
  role       = module.new-on-netflix-catalog.lambda_role_name
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