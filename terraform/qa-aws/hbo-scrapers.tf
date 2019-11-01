module "hbo-whats-new-scraper" {
  source = "./scraper-lambda"

  handler_function = "index.hboWhatsNew"
  function_name    = "hbo-whats-new"
}

module "hbo-catalog-scraper" {
  source = "./scraper-lambda"

  handler_function = "index.hboCatalog"
  function_name    = "hbo-catalog"

  create_default_trigger = false
}

resource "aws_cloudwatch_event_rule" "scraper_lambda_event_rule" {
  count               = 4
  name                = "${module.hbo-catalog-scraper.lambda_name}-trigger-band-${count.index}"
  description         = "Run ${module.hbo-catalog-scraper.lambda_name}"
  schedule_expression = "cron(${count.index * 5} 5 * * ? *)"
}

resource "aws_cloudwatch_event_target" "scraper_lambda_event_target" {
  count = 16

  arn = module.hbo-catalog-scraper.lambda_arn
  input = jsonencode({
    "mod"  = 16,
    "band" = count.index
  })
  rule = element(aws_cloudwatch_event_rule.scraper_lambda_event_rule, count.index % 4).name
}
