locals {
  teletracker_qa_cluster_name = "teletracker-qa"
}

resource "aws_ecs_cluster" "teletracker-qa" {
  name = local.teletracker_qa_cluster_name

  capacity_providers = [module.es_ingest_capacity_provider.capacity_provider_name]
}

resource "aws_ecs_cluster" "teletracker_crawlers" {
  name = "teletracker-crawlers-qa"
}

//resource "aws_ecs_capacity_provider" "consumer_capacity_provider" {
//  name = "consumer_capacity_provider"
//
//  auto_scaling_group_provider {
//    auto_scaling_group_arn         = aws_autoscaling_group.consumer_autoscaling_group.arn
//    managed_termination_protection = "ENABLED"
//
//    managed_scaling {
//      status                    = "ENABLED"
//      maximum_scaling_step_size = 1
//    }
//  }
//}
//
//data "template_file" "consumer_user_data" {
//  template = file("${path.module}/files/t3a-template-user-data.txt")
//  vars = {
//    cluster = local.teletracker_qa_cluster_name
//    purpose = "consumer"
//  }
//}
//
//resource "aws_launch_template" "consumer_launch_template" {
//  key_name      = "teletracker-qa"
//  image_id      = "ami-0cc2d77951f6af376"
//  instance_type = "t3a.micro"
//
//  instance_initiated_shutdown_behavior = "terminate"
//  ebs_optimized                        = false
//
//  user_data = base64encode(data.template_file.consumer_user_data.rendered)
//
//  block_device_mappings {
//    device_name = "/dev/xvda"
//
//    ebs {
//      delete_on_termination = true
//      encrypted             = false
//      snapshot_id           = "snap-0f6cf4b4deae5f20a"
//      volume_size           = 30
//      volume_type           = "gp2"
//    }
//  }
//
//  credit_specification {
//    cpu_credits = "standard"
//  }
//
//  iam_instance_profile {
//    arn = data.aws_iam_instance_profile.ecs-instance-profile.arn
//  }
//
//  monitoring {
//    enabled = false
//  }
//
//  network_interfaces {
//    delete_on_termination       = true
//    security_groups             = ["sg-0274044ba76f52e41", aws_security_group.ssh_access.id]
//  }
//
//  placement {
//    tenancy = "default"
//  }
//
//  instance_market_options {
//    market_type = "spot"
//    spot_options {
//      max_price                      = "0.0094"
//      instance_interruption_behavior = "terminate"
//    }
//  }
//}
//
//resource "aws_autoscaling_group" "consumer_autoscaling_group" {
//  availability_zones = data.aws_availability_zones.availability_zones.names
//
//  max_size            = 5
//  min_size            = 0
//  desired_capacity    = 0
//  vpc_zone_identifier = data.aws_subnet_ids.teletracker-subnet-ids.ids
//
//  protect_from_scale_in = true
//
//  launch_template {
//    id      = aws_launch_template.consumer_launch_template.id
//    version = aws_launch_template.consumer_launch_template.latest_version
//  }
//
//  lifecycle {
//    ignore_changes = [
//      desired_capacity]
//  }
//
//  tag {
//    key = "AmazonECSManaged"
//    propagate_at_launch = true
//    value = ""
//  }
//}