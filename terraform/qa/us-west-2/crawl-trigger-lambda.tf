resource "aws_iam_role" "crawl_trigger_role" {
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

  path = "/service-role/"
}

data "aws_iam_policy_document" "crawl_trigger_policy_doc" {
  statement {
    effect = "Allow"
    actions = [
    "lambda:InvokeFunction"]
    resources = [
    "${aws_lambda_function.crawl_trigger_function.arn}*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
    "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "dynamodb:DescribeStream",
      "dynamodb:GetRecords",
      "dynamodb:GetShardIterator",
    "dynamodb:ListStreams"]
    resources = [
    "${data.aws_dynamodb_table.crawls_table.arn}/stream/*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "sqs:SendMessage"
    ]
    resources = [
      module.task-consumer.queue_arn
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:Get*"
    ]
    resources = [
      data.aws_s3_bucket.teletracker_config_bucket.arn
    ]
  }
}

resource "aws_iam_policy" "crawl_trigger_policy" {
  name   = "CrawlTriggerLambdaPolicy"
  policy = data.aws_iam_policy_document.crawl_trigger_policy_doc.json
}

resource "aws_iam_role_policy_attachment" "crawl_trigger_policy_attachment" {
  policy_arn = aws_iam_policy.crawl_trigger_policy.arn
  role       = aws_iam_role.crawl_trigger_role.name
}

data "aws_s3_bucket_object" "crawler_artifact" {
  bucket = aws_s3_bucket.teletracker-artifacts-us-west-2.id
  key    = "lambdas/crawl_trigger.zip"
}

resource "aws_lambda_function" "crawl_trigger_function" {
  function_name = "crawl-trigger"
  handler       = "index.handler"
  role          = aws_iam_role.crawl_trigger_role.arn
  runtime       = "nodejs12.x"
  memory_size   = 128

  s3_bucket         = aws_s3_bucket.teletracker-artifacts-us-west-2.id
  s3_key            = data.aws_s3_bucket_object.crawler_artifact.key
  s3_object_version = data.aws_s3_bucket_object.crawler_artifact.version_id

  environment {
    variables = {
      TASK_QUEUE_URL = module.task-consumer.queue_id
      CONFIG_BUCKET  = data.aws_s3_bucket.teletracker_config_bucket.id
      CONFIG_KEY     = "crawl_triggers/mappings.json"
    }
  }
}

resource "aws_lambda_event_source_mapping" "crawl_trigger_dynamodb_mapping" {
  event_source_arn  = data.aws_dynamodb_table.crawls_table.stream_arn
  function_name     = aws_lambda_function.crawl_trigger_function.arn
  starting_position = "LATEST"
}