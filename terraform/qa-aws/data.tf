// TODO: Import this later
data "aws_launch_template" "teletracker-ecs-launch-template" {
  name = "Teletracker-QA-t3a"
}

data "aws_launch_template" "teletracker-ecs-launch-template-t2" {
  name = "teletracker-ecs-t2"
}

data "aws_subnet_ids" "teletracker-subnet-ids" {
  vpc_id = "vpc-95a65cfd"
}

data "aws_iam_role" "ecs-service-role" {
  name = "ecsServiceRole"
}

data "aws_iam_role" "ecs-task-execution-role" {
  name = "ecsTaskExecutionRole"
}

data "aws_iam_role" "ecs-instance-role" {
  name = "ecsInstanceRole"
}

data "aws_iam_role" "ecs-fargate-task-role" {
  name = "ecsFargateTaskRole"
}