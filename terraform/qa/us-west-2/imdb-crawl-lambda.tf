resource "aws_iam_role" "imdb_rating_crawler_role" {
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

data "aws_iam_policy_document" "imdb_rating_crawler_policy_doc" {
  statement {
    effect = "Allow"
    actions = [
    "lambda:InvokeFunction"]
    resources = [
    "${aws_lambda_function.imdb_rating_crawler_function.arn}*"]
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
      "s3:PutObject"
    ]
    resources = [
      aws_s3_bucket.teletracker-artifacts-us-west-2.arn
    ]
  }
}

resource "aws_iam_policy" "imdb_rating_crawler_policy" {
  name   = "ImdbRatingCrawlerLambdaPolicy"
  policy = data.aws_iam_policy_document.imdb_rating_crawler_policy_doc.json
}

resource "aws_iam_role_policy_attachment" "imdb_rating_crawler_s3_policy_attachment" {
  policy_arn = data.aws_iam_policy.s3_full_access_policy.arn
  role       = aws_iam_role.imdb_rating_crawler_role.name
}

resource "aws_iam_role_policy_attachment" "imdb_rating_crawler_policy_attachment" {
  policy_arn = aws_iam_policy.imdb_rating_crawler_policy.arn
  role       = aws_iam_role.imdb_rating_crawler_role.name
}

data "aws_s3_bucket_object" "imdb_rating_crawler" {
  bucket = aws_s3_bucket.teletracker-artifacts-us-west-2.id
  key    = "lambdas/imdb_crawl.zip"
}

resource "aws_lambda_function" "imdb_rating_crawler_function" {
  function_name = "imdb-rating-crawler"
  handler       = "index.handler"
  role          = aws_iam_role.imdb_rating_crawler_role.arn
  runtime       = "nodejs12.x"
  memory_size   = 128
  timeout       = 30

  s3_bucket         = aws_s3_bucket.teletracker-artifacts-us-west-2.id
  s3_key            = data.aws_s3_bucket_object.imdb_rating_crawler.key
  s3_object_version = data.aws_s3_bucket_object.imdb_rating_crawler.version_id

  environment {
    variables = {
      BUCKET = aws_s3_bucket.teletracker-data-us-west-2.id
    }
  }
}

resource "aws_cloudwatch_event_rule" "imdb_rating_crawler" {
  name                = "${aws_lambda_function.imdb_rating_crawler_function.function_name}-trigger"
  description         = "Run ${aws_lambda_function.imdb_rating_crawler_function.function_name}"
  schedule_expression = "cron(0 8 * * ? *)"
}

resource "aws_cloudwatch_event_target" "imdb_rating_crawler" {
  arn   = aws_lambda_function.imdb_rating_crawler_function.arn
  input = "{}"
  rule  = aws_cloudwatch_event_rule.imdb_rating_crawler.name
}

resource "aws_lambda_permission" "imdb_rating_crawler" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.imdb_rating_crawler_function.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.imdb_rating_crawler.arn
}
