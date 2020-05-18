resource "aws_iam_role" "ecs_events" {
  count = length(var.schedule) == 0 ? 0 : 1

  name = "${var.spider_name}_ecs_events"

  assume_role_policy = <<DOC
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "Service": "events.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
DOC
}

resource "aws_iam_role_policy" "ecs_events_run_task_with_any_role" {
  count = length(var.schedule) == 0 ? 0 : 1

  name = "${var.spider_name}_ecs_events_run_task_with_any_role"
  role = aws_iam_role.ecs_events[0].id

  policy = <<DOC
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "iam:PassRole",
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Action": "ecs:RunTask",
            "Resource": "${replace(aws_ecs_task_definition.crawler_task_def.arn, "/:\\d+$/", ":*")}"
        }
    ]
}
DOC
}

resource "aws_cloudwatch_event_rule" "cw_schedule" {
  count = length(var.schedule)

  name                = "${var.spider_name}-trigger-${count.index}"
  description         = "Run ${var.spider_name}"
  schedule_expression = var.schedule[count.index]
}

resource "aws_cloudwatch_event_target" "cw_scheduled_task" {
  count = length(var.schedule)

  target_id = "${var.spider_name}_schedule_${count.index}"
  arn       = data.aws_ecs_cluster.main_cluster.arn
  rule      = aws_cloudwatch_event_rule.cw_schedule[count.index].name
  role_arn  = aws_iam_role.ecs_events[0].arn

  ecs_target {
    task_count          = 1
    task_definition_arn = aws_ecs_task_definition.crawler_task_def.arn
    launch_type = "FARGATE"
    platform_version = "1.3.0"

    network_configuration {
      subnets = toset(data.aws_subnet_ids.teletracker-subnet-ids.*.id)
      assign_public_ip = false
      security_groups = toset([aws_security_group.crawler_sg.arn])
    }
  }
}