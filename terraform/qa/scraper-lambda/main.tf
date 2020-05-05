locals {
  env_vars = {
    BACKEND  = "aws"
    NODE_ENV = "production"
    # API_HOST          = "https://api.qa.teletracker.app"
    # ADMINISTRATOR_KEY = "berglas://teletracker-secrets/administrator-key-qa"
    # TMDB_API_KEY      = "berglas://teletracker-secrets/tmdb-api-key-qa"
  }
}

resource "aws_iam_role" "iam_for_lambda" {
  name = "iam_for_lambda-${var.function_name}"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "lambda-attach-basic-execution" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = data.aws_iam_policy.lambda_basic_execution_policy.arn
}

resource "aws_iam_role_policy_attachment" "lambda-attach-s3-policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = data.aws_iam_policy.lambda_s3_access_policy.arn
}

resource "aws_iam_role_policy_attachment" "lambda-attach-sqs-policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = data.aws_iam_policy.lambda_sqs_access_policy.arn
}

resource "aws_sqs_queue" "lambda-dead-letter-queue" {
  name = "${var.function_name}-dlq"
}

resource "aws_lambda_function" "scraper_lambda" {
  s3_bucket         = data.aws_s3_bucket_object.scrapers-artifact.bucket
  s3_key            = data.aws_s3_bucket_object.scrapers-artifact.key
  s3_object_version = data.aws_s3_bucket_object.scrapers-artifact.version_id
  function_name     = var.function_name
  role              = aws_iam_role.iam_for_lambda.arn
  handler           = var.handler_function
  timeout           = var.timeout
  memory_size       = var.memory
  runtime           = var.runtime

  environment {
    variables = {
      for key, value in merge(var.extra_env_vars, local.env_vars) :
      key => value
    }
  }

  dead_letter_config {
    target_arn = aws_sqs_queue.lambda-dead-letter-queue.arn
  }
}

resource "aws_cloudwatch_event_rule" "scraper_lambda_event_rule" {
  count               = var.create_default_trigger ? 1 : 0
  name                = "${aws_lambda_function.scraper_lambda.function_name}-trigger"
  description         = "Run ${aws_lambda_function.scraper_lambda.function_name}"
  schedule_expression = "cron(${var.cron_schedule})"
}

resource "aws_cloudwatch_event_target" "scraper_lambda_event_target" {
  count = var.create_default_trigger ? 1 : 0

  arn   = aws_lambda_function.scraper_lambda.arn
  input = var.trigger_input
  rule  = element(aws_cloudwatch_event_rule.scraper_lambda_event_rule, count.index).name
}

resource "aws_lambda_permission" "scraper_lambda_event_permission" {
  count = var.create_default_trigger ? 1 : 0

  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.scraper_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = element(aws_cloudwatch_event_rule.scraper_lambda_event_rule, count.index).arn
}
