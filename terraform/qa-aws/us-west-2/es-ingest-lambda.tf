data "aws_s3_bucket_object" "es-ingest-lambda-package" {
  bucket = aws_s3_bucket.teletracker-artifacts-us-west-2.bucket
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

resource "aws_iam_role_policy_attachment" "es-ingest-kms-attachment" {
  policy_arn = aws_iam_policy.kms_encrypt_decrypt_policy.arn
  role       = aws_iam_role.es-ingest-lambda-iam-role.name
}

resource "aws_iam_role_policy_attachment" "es-ingest-ssm-attachment" {
  policy_arn = data.aws_iam_policy.ssm_read_only_policy.arn
  role       = aws_iam_role.es-ingest-lambda-iam-role.name
}

resource "aws_iam_role_policy_attachment" "es-ingest-basic-execution-attachment" {
  policy_arn = data.aws_iam_policy.lambda_basic_execution.arn
  role       = aws_iam_role.es-ingest-lambda-iam-role.name
}

resource "aws_iam_policy" "lambda_sqs_ingest" {
  name        = "Lambda_SQS_Ingest"
  path        = "/"
  description = "Ability for lambda to read and ack messages"

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "sqs:DeleteMessage",
                "sqs:ReceiveMessage",
                "sqs:GetQueueAttributes"
            ],
            "Resource": "*"
        }
    ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "es-ingest-lambda-sqs-ingest-policy" {
  policy_arn = aws_iam_policy.lambda_sqs_ingest.arn
  role       = aws_iam_role.es-ingest-lambda-iam-role.name
}

resource "aws_lambda_function" "es-ingest-lambda" {
  s3_bucket         = aws_s3_bucket.teletracker-artifacts-us-west-2.bucket
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
      ES_HOST = aws_elasticsearch_domain.teletracker-qa-es.endpoint
      # ES_PASSWORD = "teletracker-qa-elasticsearch-password"
      # ES_USERNAME = "teletracker"
    }
  }

  reserved_concurrent_executions = 10
}

resource "aws_lambda_event_source_mapping" "es-ingest-lambda-queue-mapping" {
  event_source_arn = aws_sqs_queue.teletracker-es-ingest-queue.arn
  function_name    = aws_lambda_function.es-ingest-lambda.function_name
}
