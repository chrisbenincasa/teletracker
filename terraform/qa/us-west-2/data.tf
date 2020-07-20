// TODO: Import this later
# data "aws_launch_template" "teletracker-ecs-launch-template" {
#   name = "Teletracker-QA-t3a"
# }

# data "aws_launch_template" "teletracker-ecs-launch-template-t2" {
# name = "teletracker-ecs-t2"
# }

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

data "aws_subnet_ids" "teletracker-subnet-ids" {
  vpc_id = "vpc-09a64ee30f2e3e82e"
}

data "aws_iam_role" "ecs-service-role" {
  name = "ecsServiceRole"
}

data "aws_iam_role" "ecs-autoscalng-role" {
  name = "AWSServiceRoleForApplicationAutoScaling_ECSService"
}

data "aws_iam_role" "ecs-task-execution-role" {
  name = "ecsTaskExecutionRole"
}

data "aws_iam_role" "ecs-instance-role" {
  name = "ecsInstanceRole"
}

data "aws_iam_instance_profile" "ecs-instance-profile" {
  name = "ecsInstanceRole"
}

data "aws_iam_role" "ecs-fargate-task-role" {
  name = "ecsFargateTaskRole"
}

data "aws_iam_policy" "sqs_full_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSQSFullAccess"
}

data "aws_iam_policy" "s3_full_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

data "aws_ssm_parameter" "datadog_api_key" {
  name            = "datadog-api-key"
  with_decryption = true
}

data "aws_dynamodb_table" "crawls_table" {
  name = "teletracker.qa.crawls"
}