data "aws_ssm_parameter" "datadog_api_key" {
  name            = "datadog-api-key"
  with_decryption = true
}

data "aws_iam_role" "ecs-task-execution-role" {
  name = "ecsTaskExecutionRole"
}

data "aws_iam_role" "ecs-fargate-task-role" {
  name = "ecsFargateTaskRole"
}

data "aws_iam_policy" "s3_full_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

data "aws_vpc" "teletracker-qa-vpc" {
  id = "vpc-09a64ee30f2e3e82e"
}

data "aws_subnet_ids" "teletracker-subnet-ids" {
  vpc_id = "vpc-09a64ee30f2e3e82e"
}

data "aws_ecs_cluster" "main_cluster" {
  cluster_name = var.cluster_name
}

data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

data "aws_s3_bucket" "teletracker-artifacts-us-west-2" {
  bucket = "us-west-2-teletracker-artifacts"
}

data "aws_security_group" "all_crawlers" {
  id = "sg-0e8a76ce422567bdd"
}