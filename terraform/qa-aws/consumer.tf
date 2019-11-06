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
  //  network_mode             = "bridge"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
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
    security_groups  = ["sg-01310de0b78845ffc"]
  }

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

resource "aws_appautoscaling_target" "consumer-ecs-scale-down-target" {
  max_capacity       = 1
  min_capacity       = 0
  resource_id        = "service/${aws_ecs_cluster.teletracker-qa.name}/${aws_ecs_service.teletracker-qa-consumer.name}"
  role_arn           = data.aws_iam_role.ecs-service-role.arn
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "consumer-ecs-scale-up-policy" {
  name               = "Scale-up-consumer"
  resource_id        = aws_appautoscaling_target.consumer-ecs-scale-down-target.resource_id
  scalable_dimension = aws_appautoscaling_target.consumer-ecs-scale-down-target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.consumer-ecs-scale-down-target.service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Maximum"

    step_adjustment {
      metric_interval_upper_bound = 0
      scaling_adjustment          = 1
    }
  }
}

resource "aws_appautoscaling_policy" "consumer-ecs-scale-down-policy" {
  name               = "Scale-down-consumer"
  resource_id        = aws_appautoscaling_target.consumer-ecs-scale-down-target.resource_id
  scalable_dimension = aws_appautoscaling_target.consumer-ecs-scale-down-target.scalable_dimension
  service_namespace  = aws_appautoscaling_target.consumer-ecs-scale-down-target.service_namespace

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