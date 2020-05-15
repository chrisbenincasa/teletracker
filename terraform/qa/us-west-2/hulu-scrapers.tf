module "hulu-catalog-scraper" {
  source = "../scraper-lambda"

  handler_function = "index.huluCatalog"
  function_name    = "hulu-catalog"

  memory = 256

  create_default_trigger = false

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

module "hulu-catalog-dump" {
  source = "../scraper-lambda"

  handler_function = "index.huluCatalogDump"
  function_name    = "hulu-catalog-dump"

  memory = 256

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

module "hulu-catalog-scheduler" {
  source = "../scraper-lambda"

  handler_function = "index.scheduleHuluCatalogS3"
  function_name    = "hulu-catalog-scheduler"

  create_default_trigger = false

  s3_bucket = var.scraper-s3-bucket

  extra_env_vars = {
    DATA_BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
  }
}

resource "aws_lambda_permission" "hulu-catalog-dump-allow-teletracker-data" {
  action        = "lambda:InvokeFunction"
  function_name = module.hulu-catalog-scheduler.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.teletracker-data-us-west-2.arn
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

module "hulu-catalog-watcher" {
  source = "../scraper-lambda"

  handler_function = "index.huluCatalogWatcher"
  function_name    = "hulu-catalog-watcher"

  cron_schedule = "*/10 8 * * ? *"

  extra_env_vars = {
    EXPECTED_SIZE  = 32
    TASK_QUEUE_URL = module.task-consumer.queue_id
    DATA_BUCKET    = aws_s3_bucket.teletracker-data-us-west-2.id
  }

  s3_bucket = var.scraper-s3-bucket
}

resource "aws_iam_role_policy_attachment" "hulu-catalog-watcher-sqs-full-attachment" {
  policy_arn = data.aws_iam_policy.sqs_full_access_policy.arn
  role       = module.hulu-catalog-watcher.lambda_role_name
}
