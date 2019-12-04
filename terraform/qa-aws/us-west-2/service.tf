data "template_file" "teletracker-qa-server-task-definition-template" {
  template = file("${path.module}/task-definitions/teletracker-qa-server-task-definition.json")
  vars = {
    image = var.server_image
  }
}

resource "aws_ecs_task_definition" "teletracker-qa-server" {
  family                = "teletracker-qa-1"
  container_definitions = data.template_file.teletracker-qa-server-task-definition-template.rendered
  execution_role_arn    = data.aws_iam_role.ecs-task-execution-role.arn
  #   task_role_arn         = "arn:aws:iam::302782651551:role/ecsServiceRole"

  cpu          = 1024
  network_mode = "bridge"
}

resource "aws_ecs_service" "teletracker-qa-server" {
  name            = "teletracker-qa-server_v2"
  cluster         = aws_ecs_cluster.teletracker-qa.id
  task_definition = aws_ecs_task_definition.teletracker-qa-server.arn
  desired_count   = 0
  # iam_role        = data.aws_iam_role.ecs-service-role.name
  #   depends_on = ["${data.}"]

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

  # load_balancer {
  #   target_group_arn = aws_lb_target_group.teletracker-qa-server.arn
  #   container_name   = "teletracker-server"
  #   container_port   = 3001
  # }

  lifecycle {
    ignore_changes = ["desired_count"]
  }

  placement_constraints {
    expression = "attribute:ecs.instance-type =~ t3a.*"
    type       = "memberOf"
  }
}
