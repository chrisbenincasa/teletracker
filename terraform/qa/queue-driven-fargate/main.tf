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

// Fargate based Consumer
resource "aws_ecs_task_definition" "teletracker-qa-consumer" {
  family                = var.service_name
  container_definitions = data.template_file.teletracker-qa-consumer-task-definition-template.rendered
  execution_role_arn    = data.aws_iam_role.ecs-task-execution-role.arn
  task_role_arn         = data.aws_iam_role.ecs-fargate-task-role.arn

  cpu    = var.cpu
  memory = var.memory
  //  Bring back if we go back to EC2 style
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
}

resource "aws_security_group" "consumer_sq" {
  name        = "${var.service_name} Consumer Fargate"
  description = "Group for the ${var.service_name} Consumer Fargate ECS task"

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
  cluster         = var.cluster_id
  task_definition = aws_ecs_task_definition.teletracker-qa-consumer.arn
  desired_count   = 0
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  network_configuration {
    subnets          = data.aws_subnet_ids.teletracker-subnet-ids.ids
    assign_public_ip = true
    security_groups  = [aws_security_group.consumer_sq.id]
  }

  lifecycle {
    ignore_changes = [desired_count]
  }
}
