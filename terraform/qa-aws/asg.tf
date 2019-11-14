resource "aws_autoscaling_group" "teletracker-ecs-asg" {
  availability_zones = ["us-west-1a", "us-west-1c"]
  max_size           = 1
  min_size           = 1
  desired_capacity   = 1

  vpc_zone_identifier = data.aws_subnet_ids.teletracker-subnet-ids.ids

  launch_template {
    id      = aws_launch_template.ecs-t3a-launch-template.id
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

data "template_file" "ecs-t3a-user-data" {
  template = file("${path.module}/files/t3a-template-user-data.txt")
}


resource "aws_launch_template" "ecs-t3a-launch-template" {
  key_name      = "teletracker-qa"
  image_id      = "ami-00303cd65a37d033b"
  instance_type = "t3a.micro"

  instance_initiated_shutdown_behavior = "stop"
  ebs_optimized                        = false

  user_data = base64encode(data.template_file.ecs-t3a-user-data.rendered)

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = false
      snapshot_id           = "snap-089c5c5a00e83a014"
      volume_size           = 8
      volume_type           = "gp2"
    }
  }

  credit_specification {
    cpu_credits = "standard"
  }

  iam_instance_profile {
    arn = data.aws_iam_instance_profile.ecs-instance-profile.arn
  }

  monitoring {
    enabled = false
  }

  network_interfaces {
    associate_public_ip_address = true
    delete_on_termination       = true
    device_index                = 0

    ipv4_address_count = 0
    ipv4_addresses     = []
    ipv6_address_count = 0
    ipv6_addresses     = []

    security_groups = [
      "sg-04c19bc04eb464de5"
    ]
    subnet_id = "subnet-97a65cff"
  }

  placement {
    tenancy = "default"
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
