data "aws_ssm_parameter" "datadog_api_key" {
  name = "datadog-api-key"
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
  cluster_name = "teletracker-qa"
}