resource "aws_appautoscaling_policy" "app_policy" {
  name                    = "${var.cluster_name}_${var.service_name}_${var.name}"
  adjustment_type         = var.adjustment_type
  cooldown                = var.cooldown_seconds
  resource_id             = var.ecs_target_resource_id
  scalable_dimension      = var.scaling_dimension
  service_namespace       = var.service_namespace
  metric_aggregation_type = var.metric_aggregation

  step_adjustment {
    metric_interval_lower_bound = var.lower_bound
    metric_interval_upper_bound = var.upper_bound
    scaling_adjustment          = var.scaling_adjustment
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_alarm" {
  alarm_name          = "${var.cluster_name}_${var.service_name}_${var.name} alarm"
  comparison_operator = var.comparison_operator
  evaluation_periods  = var.evaluation_periods
  metric_name         = var.metric_name
  namespace           = var.namespace
  period              = var.period_seconds
  statistic           = var.statistic
  threshold           = var.threshold

  dimensions {
    QueueName = var.queue_name
  }

  alarm_description = var.description
  alarm_actions     = [aws_appautoscaling_policy.app_policy.arn]
}
