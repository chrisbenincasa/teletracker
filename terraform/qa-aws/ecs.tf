data "aws_iam_role" "ecs-service-role" {
  name = "ecsServiceRole"
}

data "aws_iam_role" "ecs-task-execution-role" {
  name = "ecsTaskExecutionRole"
}

resource "aws_ecs_cluster" "teletracker-qa" {
  name = "teletracker-qa"
}

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

resource "aws_autoscaling_group" "teletracker-ecs-asg" {
  availability_zones = ["us-west-1a", "us-west-1c"]
  desired_capacity   = 1
  max_size           = 1
  min_size           = 1

  vpc_zone_identifier = data.aws_subnet_ids.teletracker-subnet-ids.ids

  launch_template {
    id      = data.aws_launch_template.teletracker-ecs-launch-template.id
    version = "$Latest"
  }
}

resource "aws_autoscaling_group" "teletracker-ecs-asg-t2" {
  availability_zones = ["us-west-1a", "us-west-1c"]
  desired_capacity   = 0
  max_size           = 0
  min_size           = 0

  vpc_zone_identifier = data.aws_subnet_ids.teletracker-subnet-ids.ids

  launch_template {
    id      = data.aws_launch_template.teletracker-ecs-launch-template-t2.id
    version = "$Latest"
  }
}
