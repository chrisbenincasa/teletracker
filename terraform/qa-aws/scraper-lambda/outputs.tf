output "lambda_role_name" {
  value = aws_iam_role.iam_for_lambda.name
}

output "lambda_name" {
  value = aws_lambda_function.scraper_lambda.function_name
}

output "lambda_arn" {
  value = aws_lambda_function.scraper_lambda.arn
}
