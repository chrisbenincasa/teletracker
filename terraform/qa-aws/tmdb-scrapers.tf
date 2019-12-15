module "tmdb-changes-scraper" {
  source = "./scraper-lambda"

  handler_function = "index.tmdbChanges"
  function_name    = "tmdb-changes"

  extra_env_vars = {
    TASK_QUEUE_URL = aws_sqs_queue.teletracker-task-queue.id
  }
}

resource "aws_iam_role_policy_attachment" "tmdb-changes-scraper-ssm-attachment" {
  policy_arn = data.aws_iam_policy.ssm_read_only_policy.arn
  role       = module.tmdb-changes-scraper.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "tmdb-changes-scraper-kms-attachment" {
  policy_arn = data.aws_iam_policy.kms_power_user_policy.arn
  role       = module.tmdb-changes-scraper.lambda_role_name
}

resource "aws_iam_role_policy_attachment" "tmdb-changes-scraper-kms-encrypt-attachment" {
  policy_arn = aws_iam_policy.kms_encrypt_decrypt_policy.arn
  role       = module.tmdb-changes-scraper.lambda_role_name
}

resource "aws_cloudwatch_event_rule" "tmdb-id-dump-event-rule" {
  name                = "tmdb-dump-all-ids-trigger"
  description         = "Run DumpAllIds job"
  schedule_expression = "cron(0 8 * * ? *)"
}

resource "aws_cloudwatch_event_target" "tmdb-id-dump-event-target" {
  arn  = aws_sqs_queue.teletracker-task-queue.arn
  rule = aws_cloudwatch_event_rule.tmdb-id-dump-event-rule.name
  input = jsonencode({
    "clazz" = "com.teletracker.tasks.tmdb.DumpAllIds",
    "args" = {
      "itemType" = "all"
    }
  })
}

module "tmdb-popularity-scheduler" {
  source = "./scraper-lambda"

  function_name    = "tmdb-popularity-scheduler"
  handler_function = "index.tmdbPopularityScheduler"

  create_default_trigger = false
}

resource "aws_lambda_permission" "tmdb-popularity-scheduler-allow-teletracker-data" {
  action        = "lambda:InvokeFunction"
  function_name = module.tmdb-popularity-scheduler.lambda_name
  principal     = "s3.amazonaws.com"
  source_arn    = data.aws_s3_bucket.teletracker-data-bucket.arn
}

resource "aws_iam_role_policy_attachment" "tmdb-changes-scraper-kms-encrypt-attachment" {
  policy_arn = data.aws_iam_policy.sqs_full_access_policy.arn
  role       = module.tmdb-popularity-scheduler.lambda_role_name
}