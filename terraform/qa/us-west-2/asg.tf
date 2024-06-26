resource "aws_autoscaling_group" "teletracker-ecs-asg" {
  availability_zones = data.aws_availability_zones.availability_zones.names
  max_size           = 2
  min_size           = 2
  desired_capacity   = 2

  vpc_zone_identifier = data.aws_subnet_ids.teletracker-subnet-ids.ids

  launch_template {
    id      = aws_launch_template.ecs-t3a-launch-template.id
    version = aws_launch_template.ecs-t3a-launch-template.latest_version
  }
}

resource "aws_autoscaling_group" "crawl_ecs_asg" {
  # availability_zones = ["us-west-2a", "us-west-2b", "us-west-2c", "us-west-2d"]
  availability_zones = ["us-west-2c"]
  max_size           = 0
  min_size           = 0
  desired_capacity   = 0

  vpc_zone_identifier = ["subnet-0866af572b483b24c"]

  launch_template {
    id      = aws_launch_template.crawl_cluster_launch_template.id
    version = aws_launch_template.crawl_cluster_launch_template.latest_version
  }
}

data "template_file" "ecs-t3a-user-data" {
  template = file("${path.module}/files/t3a-template-user-data.txt")
  vars = {
    cluster = aws_ecs_cluster.teletracker-qa.name
    purpose = "server"
  }
}

resource "aws_launch_template" "ecs-t3a-launch-template" {
  key_name      = "teletracker-qa"
  image_id      = "ami-0cc2d77951f6af376"
  instance_type = "t3a.micro"

  instance_initiated_shutdown_behavior = "terminate"
  ebs_optimized                        = false

  user_data = base64encode(data.template_file.ecs-t3a-user-data.rendered)

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = false
      snapshot_id           = "snap-0f6cf4b4deae5f20a"
      volume_size           = 30
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
    delete_on_termination = true
    security_groups = [aws_security_group.ssh_access.id, aws_security_group.load_balancer_access.id]
  }

  placement {
    tenancy = "default"
  }

  instance_market_options {
    market_type = "spot"
    spot_options {
      max_price                      = "0.0094"
      instance_interruption_behavior = "terminate"
    }
  }
}


data "template_file" "crawl_cluster_template" {
  template = file("${path.module}/files/t3a-template-user-data.txt")
  vars = {
    cluster = aws_ecs_cluster.teletracker_crawlers.name
    purpose = "crawls"
  }
}

resource "aws_launch_template" "crawl_cluster_launch_template" {
  key_name      = "teletracker-qa"
  image_id      = "ami-0cc2d77951f6af376"
  instance_type = "t3a.micro"

  instance_initiated_shutdown_behavior = "terminate"
  ebs_optimized                        = false

  user_data = base64encode(data.template_file.crawl_cluster_template.rendered)

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = false
      snapshot_id           = "snap-0f6cf4b4deae5f20a"
      volume_size           = 30
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
    security_groups = [
      "sg-0590028b2b63d2325"
    ]
  }

  placement {
    tenancy = "default"
  }

  instance_market_options {
    market_type = "spot"
    spot_options {
      max_price                      = "0.0094"
      instance_interruption_behavior = "terminate"
    }
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
