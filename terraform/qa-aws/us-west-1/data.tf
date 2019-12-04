data "aws_launch_template" "teletracker-ecs-launch-template-t2" {
  name = "teletracker-ecs-t2"
}

data "aws_subnet_ids" "teletracker-subnet-ids" {
  vpc_id = "vpc-95a65cfd"
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

data "aws_iam_policy" "ssm_read_only_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess"
}

data "aws_iam_policy" "kms_power_user_policy" {
  arn = "arn:aws:iam::aws:policy/AWSKeyManagementServicePowerUser"
}

data "aws_iam_policy" "sqs_full_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSQSFullAccess"
}

data "aws_iam_policy" "s3_full_access_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}
