data "aws_s3_bucket_object" "tmdb-pull-lambda-package" {
  bucket = aws_s3_bucket.teletracker-artifacts-us-west-2.bucket
  key    = "lambdas/tmdb_pull.zip"
}

resource "aws_iam_role" "tmdb-pull-lambda-iam-role" {
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

resource "aws_iam_role_policy_attachment" "tmdb-pull-lambda-kms-attachment" {
  policy_arn = aws_iam_policy.kms_encrypt_decrypt_policy.arn
  role       = aws_iam_role.tmdb-pull-lambda-iam-role.name
}

resource "aws_iam_role_policy_attachment" "tmdb-pull-lambda-ssm-attachment" {
  policy_arn = data.aws_iam_policy.ssm_read_only_policy.arn
  role       = aws_iam_role.tmdb-pull-lambda-iam-role.name
}

resource "aws_iam_role_policy_attachment" "tmdb-pull-lambda-basic-execution-attachment" {
  policy_arn = data.aws_iam_policy.lambda_basic_execution.arn
  role       = aws_iam_role.tmdb-pull-lambda-iam-role.name
}

resource "aws_lambda_function" "tmdb-pull-lambda" {
  s3_bucket         = aws_s3_bucket.teletracker-artifacts-us-west-2.bucket
  s3_key            = data.aws_s3_bucket_object.tmdb-pull-lambda-package.key
  s3_object_version = data.aws_s3_bucket_object.tmdb-pull-lambda-package.version_id
  function_name     = "tmdb-pull"
  role              = aws_iam_role.tmdb-pull-lambda-iam-role.arn
  handler           = "index.seasons"
  timeout           = 10
  memory_size       = 128

  runtime = "nodejs12.x"

  environment {
    variables = {
      ES_HOST = aws_elasticsearch_domain.teletracker-qa-es.endpoint
    }
  }
}
