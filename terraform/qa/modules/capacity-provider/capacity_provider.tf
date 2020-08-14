resource "aws_ecs_capacity_provider" "consumer_capacity_provider" {
  name = "${var.service_name}consumer_capacity_provider"

  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.consumer_autoscaling_group.arn
    managed_termination_protection = "ENABLED"

    managed_scaling {
      status                    = "ENABLED"
      maximum_scaling_step_size = 1
    }
  }
}