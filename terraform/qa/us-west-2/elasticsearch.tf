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
}

data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

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
