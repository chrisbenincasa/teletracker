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
  #   task_role_arn         = "arn:aws:iam::302782651551:role/ecsServiceRole"

  cpu          = 1024
  network_mode = "bridge"
}

resource "aws_ecs_service" "teletracker-qa-consumer" {
  name            = "teletracker-qa-consumer_v2"
  cluster         = aws_ecs_cluster.teletracker-qa.id
  task_definition = aws_ecs_task_definition.teletracker-qa-consumer.arn
  desired_count   = 1

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  ordered_placement_strategy {
    field = "attribute:ecs.availability-zone"
    type  = "spread"
  }

  ordered_placement_strategy {
    field = "instanceId"
    type  = "spread"
  }

  lifecycle {
    ignore_changes = ["desired_count"]
  }

  placement_constraints {
    expression = "attribute:ecs.instance-type =~ t3a.*"
    type       = "memberOf"
  }
}
