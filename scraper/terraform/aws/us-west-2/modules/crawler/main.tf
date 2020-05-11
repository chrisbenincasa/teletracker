data "template_file" "task_definition_tpl" {
  template = file("${path.module}/task-definitions/crawler-task-definition.json")
  vars = {
    name = var.name
    image = var.crawler_image,
    version = var.image_version,
    service_name = var.name
    datadog_api_key = data.aws_ssm_parameter.datadog_api_key.value
    spider_name = var.spider_name
    s3_directory = var.s3_directory
    s3_subdirectory = var.s3_path
    output_format = var.output_format
  }
}

resource "aws_ecs_task_definition" "crawler_task_def" {
  family                = var.name
  container_definitions = data.template_file.task_definition_tpl.rendered
  execution_role_arn    = data.aws_iam_role.ecs-task-execution-role.arn
  task_role_arn         = data.aws_iam_role.ecs-fargate-task-role.arn

  cpu    = 256
  memory = 512
  //  Bring back if we go back to EC2 style
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
}

resource "aws_security_group" "crawler_sg" {
  name        = "${var.name} Fargate"
  description = "Group for the ${var.name} Fargate ECS task"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_ecs_service" "crawler_ecs_service" {
  name            = var.name
  cluster         = data.aws_ecs_cluster.main_cluster.id
  task_definition = aws_ecs_task_definition.crawler_task_def.arn
  desired_count   = 0
  launch_type     = "FARGATE"

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  network_configuration {
    subnets          = data.aws_subnet_ids.teletracker-subnet-ids.ids
    assign_public_ip = true
    security_groups  = [aws_security_group.crawler_sg.id]
  }

  lifecycle {
    ignore_changes = ["desired_count"]
  }
}
