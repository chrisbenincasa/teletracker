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

data "aws_sqs_queue" "consumer_queue" {
  name = var.fifo ? "${var.queue_name}.fifo" : var.queue_name
}

data "aws_availability_zones" "availability_zones" {
  state = "available"
}

data "aws_ecs_cluster" "cluster" {
  cluster_name = var.cluster_name
}