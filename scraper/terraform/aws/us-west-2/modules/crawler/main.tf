resource "aws_ecs_task_definition" "crawler_task_def" {
  family = var.name

  container_definitions = jsonencode([
    {
      name: var.name,
      image: "${var.crawler_image}:${var.image_version}",
      cpu: 256,
      memory: 512,
      essential: true,
      logConfiguration: {
        logDriver: "awslogs",
        options: {
          "awslogs-group": "${var.spider_name}_crawler",
          "awslogs-region": "us-west-2",
          "awslogs-stream-prefix": "${var.spider_name}_crawler",
          "awslogs-create-group": "true"
        }
      },
      environment: concat(var.dynamodb_output_table == "" ? [] : [
        {
          name: "DYNAMO_DB_OUTPUT_TABLE",
          value: var.dynamodb_output_table
        }
      ], var.redis_host == "" ? [] : [
        {
          name: "REDIS_HOST",
          value: var.redis_host
        }
      ]),
      command: concat([
        "./run_spider.sh",
        var.spider_name], var.outputs, var.extra_args)
    }
  ])

  execution_role_arn = data.aws_iam_role.ecs-task-execution-role.arn
  task_role_arn = data.aws_iam_role.ecs-fargate-task-role.arn

  cpu = 256
  memory = 512
  //  Bring back if we go back to EC2 style
  network_mode = "awsvpc"
  requires_compatibilities = [
    "FARGATE"]
}

resource "aws_security_group" "crawler_sg" {
  name = "${var.name} Fargate"
  description = "Group for the ${var.name} Fargate ECS task"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
    ipv6_cidr_blocks = [
      "::/0"]
  }
}

output "crawler_sg_id" {
  value = aws_security_group.crawler_sg.id
}

resource "aws_ecs_service" "crawler_ecs_service" {
  count = var.gen_service ? 1 : 0

  name = var.name
  cluster = data.aws_ecs_cluster.main_cluster.id
  task_definition = aws_ecs_task_definition.crawler_task_def.arn
  desired_count = 0
  launch_type = "FARGATE"

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent = 100

  network_configuration {
    subnets = data.aws_subnet_ids.teletracker-subnet-ids.ids
    assign_public_ip = true
    security_groups = [
      aws_security_group.crawler_sg.id]
  }

  lifecycle {
    ignore_changes = [
      "desired_count"]
  }
}
