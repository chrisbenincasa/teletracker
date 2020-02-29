// Forget about this state until https://github.com/terraform-providers/terraform-provider-aws/issues/8827 is fixed
//resource "aws_cognito_user_pool" "default-user-pool" {
//  provider = "aws.us-west-2"
//
//  name = "Teletracker-QA"
//
//  username_attributes = ["email"]
//  //  auto_verified_attributes = ["email"]
//
//  sms_authentication_message = "Your authentication code is {####}. "
//
//  admin_create_user_config {
//    allow_admin_create_user_only = false
//
//    invite_message_template {
//      email_message = "Your username is {username} and temporary password is {####}."
//      email_subject = "Your temporary password"
//      sms_message   = "Your username is {username} and temporary password is {####}."
//    }
//  }
//
//  email_configuration {
//    email_sending_account = "COGNITO_DEFAULT"
//  }
//
//  schema {
//    attribute_data_type      = "String"
//    name                     = "email"
//    developer_only_attribute = false
//    mutable                  = true
//    required                 = true
//
//    string_attribute_constraints {
//      max_length = "2048"
//      min_length = "0"
//    }
//  }
//
//  password_policy {
//    minimum_length = 8
//  }
//
//  //  lambda_config {
//  //    pre_sign_up = module.cognito-signup-hook.lambda_arn
//  //  }
//}

data "aws_cognito_user_pools" "default-user-pool" {
  name = "Teletracker-QA"
}

resource "aws_cognito_user_pool_domain" "default-user-pool-domain" {
  user_pool_id    = sort(data.aws_cognito_user_pools.default-user-pool.ids)[0]
  domain          = "auth.qa.teletracker.tv"
  certificate_arn = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.arn
}

resource "aws_cognito_user_pool_client" "default-user-pool-client" {
  name = "Web"

  user_pool_id                 = sort(data.aws_cognito_user_pools.default-user-pool.ids)[0]
  generate_secret              = false
  supported_identity_providers = ["COGNITO", "Google"]

  allowed_oauth_scopes                 = ["email", "openid"]
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_flows_user_pool_client = true

  callback_urls = [
    "http://localhost:3000/login",
    "https://localhost:3000/login",
    "https://qa.teletracker.tv/login",
    "https://teletracker.local:3000/login",
    "https://ssr.qa.teletracker.tv/login"
  ]

  explicit_auth_flows = [
    "ALLOW_CUSTOM_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_SRP_AUTH",
  ]

  logout_urls = [
    "https://localhost:3000",
    "https://teletracker.local:3000",
    "https://ssr.qa.teletracker.tv"
  ]
}

module "cognito-signup-hook" {
  source = "../cognito-lambda"

  s3_bucket        = aws_s3_bucket.teletracker-artifacts-us-west-2.id
  function_name    = "cognito-signup-hook"
  handler_function = "index.signupHook"
  user_pool_arn    = sort(data.aws_cognito_user_pools.default-user-pool.arns)[0]
}
