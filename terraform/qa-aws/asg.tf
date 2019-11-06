resource "aws_autoscaling_group" "teletracker-ecs-asg" {
  availability_zones = ["us-west-1a", "us-west-1c"]
  max_size           = 1
  min_size           = 1
  desired_capacity   = 1

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

// Turn back on if we need cluster scaling.
//resource "aws_autoscaling_policy" "teletracker-qa-cluster-scale-up" {
//  autoscaling_group_name = aws_autoscaling_group.teletracker-ecs-asg.name
//  name                   = "Teletracker QA ECS Cluster scale up"
//
//  scaling_adjustment = 1
//  adjustment_type    = "ChangeInCapacity"
//}
//
//resource "aws_autoscaling_policy" "teletracker-qa-cluster-scale-down" {
//  autoscaling_group_name = aws_autoscaling_group.teletracker-ecs-asg.name
//  name                   = "Teletracker QA ECS Cluster scale down"
//
//  scaling_adjustment = -1
//  adjustment_type    = "ChangeInCapacity"
//}
