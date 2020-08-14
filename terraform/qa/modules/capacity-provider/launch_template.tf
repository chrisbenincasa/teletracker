resource "aws_launch_template" "consumer_launch_template" {
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
    delete_on_termination       = true
    security_groups             = var.security_groups
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