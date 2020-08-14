data "template_file" "teletracker-qa-consumer-task-definition-template" {
  template = file("${path.module}/task-definitions/teletracker-qa-consumer-task-definition.json")
  vars = {
    image        = var.image,
    service_name = var.service_name,
    mode         = var.consumer_mode
    cpu          = var.cpu
    memory       = var.memory
  }
}

resource "aws_ecs_task_definition" "teletracker-qa-consumer" {
  family                = var.service_name
  container_definitions = data.template_file.teletracker-qa-consumer-task-definition-template.rendered
  execution_role_arn    = data.aws_iam_role.ecs-task-execution-role.arn

  cpu          = var.cpu
  memory       = var.memory
  network_mode = "bridge"
}

resource "aws_security_group" "consumer_sg" {
  name        = "${var.service_name} Consumer"
  description = "Group for the ${var.service_name} Consumer ECS task"

  vpc_id = var.vpc_id

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_ecs_service" "consumer_ecs_service" {
  name            = var.service_name
  cluster         = data.aws_ecs_cluster.cluster.id
  task_definition = aws_ecs_task_definition.teletracker-qa-consumer.arn
  desired_count   = 0

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  capacity_provider_strategy {
    capacity_provider = var.capacity_provider
    weight            = 1
    base              = 0
  }

  lifecycle {
    ignore_changes = [desired_count]
  }
}
