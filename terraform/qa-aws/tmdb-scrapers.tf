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
      "type" = "all"
    }
  })
}