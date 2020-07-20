resource "aws_elasticsearch_domain" "teletracker-qa-es" {
  domain_name           = "teletracker-qa"
  elasticsearch_version = "7.4"

  cluster_config {
    instance_type  = "t2.small.elasticsearch"
    instance_count = 1

    dedicated_master_enabled = false
    zone_awareness_enabled   = false
  }

  snapshot_options {
    automated_snapshot_start_hour = 23
  }

  ebs_options {
    ebs_enabled = true
    volume_size = 15
  }

  #   domain_endpoint_options {
  #     enforce_https = true
  #   }

  log_publishing_options {
    cloudwatch_log_group_arn = "arn:aws:logs:us-west-2:302782651551:log-group:/aws/aes/domains/teletracker-qa/application-logs"
    enabled                  = true
    log_type                 = "ES_APPLICATION_LOGS"
  }
  log_publishing_options {
    cloudwatch_log_group_arn = "arn:aws:logs:us-west-2:302782651551:log-group:/aws/aes/domains/teletracker-qa/search-logs"
    enabled                  = true
    log_type                 = "SEARCH_SLOW_LOGS"
  }
}


resource "aws_elasticsearch_domain_policy" "main" {
  domain_name = aws_elasticsearch_domain.teletracker-qa-es.domain_name

  access_policies = <<POLICIES
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": "es:*",
            "Principal": "*",
            "Effect": "Allow",
            "Condition": {
                "IpAddress": {
                    "aws:SourceIp": [
                        "67.164.191.249",
                        "54.193.107.226",
                        "54.148.251.95",
                        "73.243.144.208",
                        "71.185.54.20",
                        "52.24.141.158"
                    ]
                }
            },
            "Resource": "${aws_elasticsearch_domain.teletracker-qa-es.arn}/*"
        },
        {
            "Action": "es:*",
            "Principal": {
                "AWS": [
                    "${data.aws_iam_role.ecs-fargate-task-role.arn}"
                ]
            },
            "Effect": "Allow",
            "Resource": "${aws_elasticsearch_domain.teletracker-qa-es.arn}/*"
        }
    ]
}
POLICIES
}

data "template_file" "elasticdump-task-definition-template" {
  template = file("${path.module}/task-definitions/elasticdump-task-definition.json")
  vars = {
    image = var.server_image
  }
}

resource "aws_ecs_task_definition" "elasticdump-task-definition" {
  family                = "elasticdump"
  container_definitions = data.template_file.elasticdump-task-definition-template.rendered
  execution_role_arn    = data.aws_iam_role.ecs-task-execution-role.arn
  task_role_arn         = data.aws_iam_role.ecs-fargate-task-role.arn

  cpu    = 256
  memory = 512

  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
}

resource "aws_security_group" "elasticdump-sg" {
  name        = "Elasticdump Fargate"
  description = "Group for the Elasticdump Fargate ECS task"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_iam_role" "elasticdump_ecs_events" {
  name = "elasticdump_ecs_events"

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

resource "aws_iam_role_policy" "elasticdump_schedule_policy" {
  name = "elasticdump_schedule_policy"
  role = aws_iam_role.elasticdump_ecs_events.id

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
            "Resource": "${replace(aws_ecs_task_definition.elasticdump-task-definition.arn, "/:\\d+$/", ":*")}"
        }
    ]
}
DOC
}

resource "aws_cloudwatch_event_rule" "elasticdump_schedule" {
  name                = "elasticdump-trigger"
  description         = "Run elasticdump"
  schedule_expression = "cron(0 5 ? * SUN *)"
}

resource "aws_cloudwatch_event_target" "elasticdump_cw_event_target" {
  target_id = "elasticdump_schedule"
  arn       = aws_ecs_cluster.teletracker-qa.arn
  rule      = aws_cloudwatch_event_rule.elasticdump_schedule.name
  role_arn  = aws_iam_role.elasticdump_ecs_events.arn

  ecs_target {
    task_count          = 1
    task_definition_arn = aws_ecs_task_definition.elasticdump-task-definition.arn
    launch_type         = "FARGATE"
    platform_version    = "1.3.0"

    network_configuration {
      subnets          = data.aws_subnet_ids.teletracker-subnet-ids.ids
      assign_public_ip = true
      security_groups  = toset([aws_security_group.elasticdump-sg.id])
    }
  }
}
