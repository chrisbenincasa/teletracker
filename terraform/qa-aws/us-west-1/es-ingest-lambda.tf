data "aws_s3_bucket_object" "es-ingest-lambda-package" {
  bucket = data.aws_s3_bucket.teletracker-artifacts-bucket.bucket
  key    = "ingest-lambda/package.zip"
}

resource "aws_iam_role" "es-ingest-lambda-iam-role" {
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

resource "aws_lambda_function" "es-ingest-lambda" {
  s3_bucket         = data.aws_s3_bucket.teletracker-artifacts-bucket.bucket
  s3_key            = data.aws_s3_bucket_object.es-ingest-lambda-package.key
  s3_object_version = data.aws_s3_bucket_object.es-ingest-lambda-package.version_id
  function_name     = "es-ingest"
  role              = aws_iam_role.es-ingest-lambda-iam-role.arn
  handler           = "index.handler"
  timeout           = 10
  memory_size       = 128

  runtime = "nodejs12.x"

  environment {
    variables = {
      ES_HOST     = "e5f0ee8ddcd64d30adc14c9bd430950e.us-west-1.aws.found.io"
      ES_PASSWORD = "teletracker-qa-elasticsearch-password"
      ES_USERNAME = "teletracker"
    }
  }

  reserved_concurrent_executions = 10
}

resource "aws_lambda_event_source_mapping" "es-ingest-lambda-queue-mapping" {
  event_source_arn = aws_sqs_queue.teletracker-es-ingest-queue.arn
  function_name    = aws_lambda_function.es-ingest-lambda.function_name
}