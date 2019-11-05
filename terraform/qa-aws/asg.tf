resource "aws_autoscaling_group" "teletracker-ecs-asg" {
  availability_zones = ["us-west-1a", "us-west-1c"]
  max_size           = 2
  min_size           = 1

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

resource "aws_autoscaling_policy" "teletracker-qa-cluster-scale-up" {
  autoscaling_group_name = aws_autoscaling_group.teletracker-ecs-asg.name
  name                   = "Teletracker QA ECS Cluster scale up"

  scaling_adjustment = 1
  adjustment_type    = "ChangeInCapacity"
}

resource "aws_autoscaling_policy" "teletracker-qa-cluster-scale-down" {
  autoscaling_group_name = aws_autoscaling_group.teletracker-ecs-asg.name
  name                   = "Teletracker QA ECS Cluster scale down"

  scaling_adjustment = -1
  adjustment_type    = "ChangeInCapacity"
}


resource "aws_cloudwatch_metric_alarm" "teletracker-qa-cluster-scale-up-alarm" {
  alarm_name          = "Teletracker-QA-Scale-Up-Alarm"
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
        QueueName = aws_sqs_queue.teletracker-task-queue.name
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
        QueueName = aws_sqs_queue.teletracker-task-queue.name
      }
    }
  }

  alarm_actions = [aws_autoscaling_policy.teletracker-qa-cluster-scale-up.arn, aws_appautoscaling_policy.consumer-ecs-scale-up-policy.arn]
}

resource "aws_cloudwatch_metric_alarm" "teletracker-qa-cluster-scale-down-alarm" {
  alarm_name = "Teletracker-QA-Scale-Down-Alarm"

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
        QueueName = aws_sqs_queue.teletracker-task-queue.name
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
        QueueName = aws_sqs_queue.teletracker-task-queue.name
      }
    }
  }

  alarm_actions = [aws_autoscaling_policy.teletracker-qa-cluster-scale-down.arn, aws_appautoscaling_policy.consumer-ecs-scale-down-policy.arn]
}