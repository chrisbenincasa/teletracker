data "aws_iam_policy" "lambda_basic_execution_policy" {
  arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy" "lambda_s3_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

data "aws_iam_policy" "lambda_sqs_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSQSFullAccess"
}

data "aws_s3_bucket_object" "scrapers-artifact" {
  bucket = var.s3_bucket
  key    = "scrapers/scrapers.zip"
}
