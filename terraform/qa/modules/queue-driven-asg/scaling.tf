resource "aws_appautoscaling_target" "consumer-ecs-scale-target" {
  max_capacity       = 1
  min_capacity       = 0
  resource_id        = "service/${var.cluster_name}/${aws_ecs_service.consumer_ecs_service.name}"
  role_arn           = data.aws_iam_role.ecs-autoscalng-role.arn
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "consumer-ecs-scale-up-policy" {
  name               = "${var.service_name}-Scale-up-consumer"
  resource_id        = aws_appautoscaling_target.consumer-ecs-scale-target.resource_id
  scalable_dimension = aws_appautoscaling_target.consumer-ecs-scale-target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.consumer-ecs-scale-target.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Maximum"

    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 1
    }
  }
}

resource "aws_appautoscaling_policy" "consumer-ecs-scale-down-policy" {
  name               = "${var.service_name}-Scale-down-consumer"
  resource_id        = aws_appautoscaling_target.consumer-ecs-scale-target.resource_id
  scalable_dimension = aws_appautoscaling_target.consumer-ecs-scale-target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.consumer-ecs-scale-target.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Maximum"

    step_adjustment {
      metric_interval_upper_bound = 0
      scaling_adjustment          = -1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "teletracker-qa-cluster-scale-up-alarm" {
  alarm_name          = "${var.service_name}-consumer-Scale-Up-Alarm"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 3
  threshold           = 1

  metric_query {
    id          = "e1"
    expression  = "visible+notvisible"
    label       = "Sum_Visible+NonVisible"
    return_data = true
  }

  metric_query {
    id = "visible"

    metric {
      namespace   = "AWS/SQS"
      metric_name = "ApproximateNumberOfMessagesVisible"
      period      = 60
      stat        = "Maximum"

      dimensions = {
        QueueName = data.aws_sqs_queue.consumer_queue.name
      }
    }
  }

  metric_query {
    id = "notvisible"

    metric {
      metric_name = "ApproximateNumberOfMessagesNotVisible"
      namespace   = "AWS/SQS"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        QueueName = data.aws_sqs_queue.consumer_queue.name
      }
    }
  }

  alarm_actions = [
    //    aws_autoscaling_policy.teletracker-qa-cluster-scale-up.arn,
    aws_appautoscaling_policy.consumer-ecs-scale-up-policy.arn
  ]
}

resource "aws_cloudwatch_metric_alarm" "teletracker-qa-cluster-scale-down-alarm" {
  alarm_name = "${var.service_name}-consumer-Scale-Down-Alarm"

  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = 5
  threshold           = 0

  metric_query {
    id          = "e1"
    expression  = "visible+notvisible"
    label       = "Sum_Visible+NonVisible"
    return_data = true
  }

  metric_query {
    id = "visible"
    metric {
      namespace   = "AWS/SQS"
      metric_name = "ApproximateNumberOfMessagesVisible"
      period      = 60
      stat        = "Maximum"

      dimensions = {
        QueueName = data.aws_sqs_queue.consumer_queue.name
      }
    }
  }

  metric_query {
    id = "notvisible"

    metric {
      metric_name = "ApproximateNumberOfMessagesNotVisible"
      namespace   = "AWS/SQS"
      period      = "60"
      stat        = "Maximum"

      dimensions = {
        QueueName = data.aws_sqs_queue.consumer_queue.name
      }
    }
  }

  alarm_actions = [
    //    aws_autoscaling_policy.teletracker-qa-cluster-scale-down.arn,
    aws_appautoscaling_policy.consumer-ecs-scale-down-policy.arn
  ]
}
