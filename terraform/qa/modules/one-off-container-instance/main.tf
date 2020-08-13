resource "aws_launch_template" "launch_template" {
  key_name      = "teletracker-qa"
  image_id      = "ami-0cc2d77951f6af376"
  instance_type = "t3a.micro"

  instance_initiated_shutdown_behavior = "terminate"
  ebs_optimized                        = false

  user_data = base64encode(var.user_data)

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = false
      snapshot_id           = var.snapshot_id
      volume_size           = 30
      volume_type           = "gp2"
    }
  }

  credit_specification {
    cpu_credits = "standard"
  }

  iam_instance_profile {
    arn = var.iam_instance_profile
  }

  monitoring {
    enabled = false
  }

  network_interfaces {
    associate_public_ip_address = length(var.network_interface_id) > 0 ? null : true
    delete_on_termination       = length(var.network_interface_id) > 0 ? false : true
    security_groups             = length(var.network_interface_id) > 0 ? [] : var.security_groups
    network_interface_id        = var.network_interface_id
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

  tag_specifications {
    resource_type = "instance"

    tags = var.tags
  }
}

data "aws_network_interface" "network_interface" {
  id = var.network_interface_id
}

resource "aws_autoscaling_group" "autoscaling_group" {
  availability_zones = [data.aws_network_interface.network_interface.availability_zone]
  max_size         = var.max
  min_size         = var.min
  desired_capacity = var.desired

  vpc_zone_identifier = null
//  vpc_zone_identifier = var.network_interface_id == "" ? [data.aws_network_interface.network_interface.subnet_id] : null

  launch_template {
    id      = aws_launch_template.launch_template.id
    version = aws_launch_template.launch_template.latest_version
  }
}
