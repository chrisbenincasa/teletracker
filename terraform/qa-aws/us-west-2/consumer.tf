data "template_file" "teletracker-qa-consumer-task-definition-template" {
  template = file("${path.module}/task-definitions/teletracker-qa-consumer-task-definition.json")
  vars = {
    image = var.consumer_image
  }
}

resource "aws_ecs_task_definition" "teletracker-qa-consumer" {
  family                = "teletracker-consumer"
  container_definitions = data.template_file.teletracker-qa-consumer-task-definition-template.rendered
  execution_role_arn    = data.aws_iam_role.ecs-task-execution-role.arn
  task_role_arn         = data.aws_iam_role.ecs-fargate-task-role.arn

  cpu    = 512
  memory = 1024
  //  Bring back if we go back to EC2 style
  //  network_mode             = "bridge"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
}

resource "aws_security_group" "teletracker-qa-consumer-sg" {
  name        = "Consumer Fargate"
  description = "Group for the Consumer Fargate ECS task"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_ecs_service" "teletracker-qa-consumer" {
  name            = "teletracker-qa-consumer_v2"
  cluster         = aws_ecs_cluster.teletracker-qa.id
  task_definition = aws_ecs_task_definition.teletracker-qa-consumer.arn
  desired_count   = 0
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  network_configuration {
    subnets          = data.aws_subnet_ids.teletracker-subnet-ids.ids
    assign_public_ip = true
    security_groups  = [aws_security_group.teletracker-qa-consumer-sg.id]
  }

  //  Bring back if we go back to EC2 style
  //  ordered_placement_strategy {
  //    field = "attribute:ecs.availability-zone"
  //    type  = "spread"
  //  }
  //
  //  ordered_placement_strategy {
  //    field = "instanceId"
  //    type  = "spread"
  //  }

  lifecycle {
    ignore_changes = ["desired_count"]
  }

  //  placement_constraints {
  //    expression = "attribute:ecs.instance-type =~ t3a.*"
  //    type       = "memberOf"
  //  }
}

resource "aws_appautoscaling_target" "consumer-ecs-scale-target" {
  max_capacity       = 1
  min_capacity       = 0
  resource_id        = "service/${aws_ecs_cluster.teletracker-qa.name}/${aws_ecs_service.teletracker-qa-consumer.name}"
  role_arn           = data.aws_iam_role.ecs-autoscalng-role.arn
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "consumer-ecs-scale-up-policy" {
  name               = "Scale-up-consumer"
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
  name               = "Scale-down-consumer"
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

  alarm_actions = [
    //    aws_autoscaling_policy.teletracker-qa-cluster-scale-up.arn,
    aws_appautoscaling_policy.consumer-ecs-scale-up-policy.arn
  ]
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

  alarm_actions = [
    //    aws_autoscaling_policy.teletracker-qa-cluster-scale-down.arn,
    aws_appautoscaling_policy.consumer-ecs-scale-down-policy.arn
  ]
}