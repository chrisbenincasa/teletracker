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

data "aws_iam_policy" "lambda_basic_execution_policy" {
  arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy" "lambda_cognito_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonCognitoPowerUser"
}

resource "aws_iam_role_policy_attachment" "lambda-attach-cognto-policy" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = data.aws_iam_policy.lambda_cognito_access_policy.arn
}

resource "aws_iam_role_policy_attachment" "lambda-attach-basic-execution" {
  role       = aws_iam_role.iam_for_lambda.name
  policy_arn = data.aws_iam_policy.lambda_basic_execution_policy.arn
}

resource "aws_lambda_function" "cognito_hook_lambda" {
  s3_bucket     = var.s3_bucket
  s3_key        = "cognito-hooks/hooks.zip"
  function_name = var.function_name
  role          = aws_iam_role.iam_for_lambda.arn
  handler       = var.handler_function
  timeout       = var.timeout
  memory_size   = var.memory

  source_code_hash = filebase64sha256("${path.module}/../../../lambda/cognito/build/hooks.zip")

  runtime = "nodejs10.x"

  environment {
    variables = {
      for key, value in merge(var.extra_env_vars, local.env_vars) :
      key => value
    }
  }
}

resource "aws_lambda_permission" "cognito_hook_trigger_from_cognito" {
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.cognito_hook_lambda.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = var.user_pool_arn
}

output "lambda_role_name" {
  value = aws_iam_role.iam_for_lambda.name
}

output "lambda_name" {
  value = aws_lambda_function.cognito_hook_lambda.function_name
}

output "lambda_arn" {
  value = aws_lambda_function.cognito_hook_lambda.arn
}
