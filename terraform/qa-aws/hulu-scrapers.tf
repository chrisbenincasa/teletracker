module "hulu-catalog-scraper" {
  source = "./scraper-lambda"

  handler_function = "index.huluCatalog"
  function_name    = "hulu-catalog"

  memory = 256

  create_default_trigger = false
}

module "hulu-catalog-dump" {
  source = "./scraper-lambda"

  handler_function = "index.huluCatalogDump"
  function_name    = "hulu-catalog-dump"

  memory = 256
}

module "hulu-catalog-scheduler" {
  source = "./scraper-lambda"

  handler_function = "index.scheduleHuluCatalogS3"
  function_name    = "hulu-catalog-scheduler"

  create_default_trigger = false
}

resource "aws_lambda_permission" "hulu-catalog-dump-allow-teletracker-data" {
  action        = "lambda:InvokeFunction"
  function_name = module.hulu-catalog-scheduler.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = data.aws_s3_bucket.teletracker-data-bucket.arn
}

resource "aws_iam_role_policy_attachment" "hulu-catalog-scheduler-lambda-invoke" {
  policy_arn = aws_iam_policy.lambda_execute.arn
  role       = module.hulu-catalog-scheduler.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "hulu-catalog-lambda-invoke" {
  policy_arn = aws_iam_policy.lambda_execute.arn
  role       = module.hulu-catalog-scraper.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "hulu-catalog-scraper-ssm-attachment" {
  policy_arn = data.aws_iam_policy.ssm_read_only_policy.arn
  role       = module.hulu-catalog-scraper.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "hulu-catalog-scraper-kms-attachment" {
  policy_arn = data.aws_iam_policy.kms_power_user_policy.arn
  role       = module.hulu-catalog-scraper.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "hulu-catalog-scraper-kms-encrypt-attachment" {
  policy_arn = aws_iam_policy.kms_encrypt_decrypt_policy.arn
  role       = module.hulu-catalog-scraper.lambda_role_name
}