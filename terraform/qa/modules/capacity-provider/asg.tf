resource "aws_autoscaling_group" "consumer_autoscaling_group" {
  availability_zones = data.aws_availability_zones.availability_zones.names

  max_size            = var.max
  min_size            = var.min
  desired_capacity    = var.desired
  vpc_zone_identifier = data.aws_subnet_ids.subnet_ids.ids

  protect_from_scale_in = true

  launch_template {
    id      = aws_launch_template.consumer_launch_template.id
    version = aws_launch_template.consumer_launch_template.latest_version
  }

  lifecycle {
    ignore_changes = [
      desired_capacity]
  }

  tag {
    key = "AmazonECSManaged"
    propagate_at_launch = true
    value = ""
  }
}