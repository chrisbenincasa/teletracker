data "aws_iam_role" "ecs-autoscalng-role" {
  name = "AWSServiceRoleForApplicationAutoScaling_ECSService"
}

data "aws_subnet_ids" "teletracker-subnet-ids" {
  vpc_id = var.vpc_id
}

data "aws_iam_role" "ecs-fargate-task-role" {
  name = "ecsFargateTaskRole"
}

data "aws_iam_role" "ecs-task-execution-role" {
  name = "ecsTaskExecutionRole"
}
