resource "aws_iam_role" "teletracker-frontend-lambda-role" {
  name_prefix = "lambda-execution-role-"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": [
          "lambda.amazonaws.com",
          "edgelambda.amazonaws.com"
        ]
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

data "aws_s3_bucket_object" "teletracker-frontend-lambda-zip" {
  provider = "aws.us-east-1"

  bucket = aws_s3_bucket.teletracker-artifacts-us-east-1.bucket
  key    = "frontend-lambda.zip"
}

resource "aws_iam_role_policy_attachment" "teletracker-frontend-lambda-basic-execution" {
  role       = aws_iam_role.teletracker-frontend-lambda-role.name
  policy_arn = data.aws_iam_policy.lambda_basic_execution.arn
}

data "aws_iam_policy_document" "teletracker-frontend-bucket-policy" {
  statement {
    sid = "1"

    actions = [
      "s3:GetObject",
    ]

    resources = [
      "arn:aws:s3:::ssr.qa.teletracker.tv/*",
    ]

    principals {
      type = "AWS"

      identifiers = [
        aws_cloudfront_origin_access_identity.teletracker-frontend.iam_arn,
      ]
    }
  }
}

resource "aws_s3_bucket" "teletracker-frontend-artifacts" {
  provider = "aws.us-east-1"

  bucket = "ssr.qa.teletracker.tv"
  region = "us-east-1"

  policy = data.aws_iam_policy_document.teletracker-frontend-bucket-policy.json

  versioning {
    enabled = true
  }
}

resource "aws_lb_target_group" "teletracker_qa_frontend" {
  name     = "teletracker-qa-frontend"
  port     = 80
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.teletracker-qa-vpc.id

  health_check {
    interval            = 30
    timeout             = 5
    healthy_threshold   = 5
    unhealthy_threshold = 5
    path                = "/health"
    protocol            = "HTTP"
  }

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [aws_lb.teletracker_qa]
}

resource "aws_ecs_task_definition" "frontend" {
  family             = "frontend"
  execution_role_arn = data.aws_iam_role.ecs-task-execution-role.arn
  container_definitions = jsonencode([
    {
      name : "teletracker-frontend",
      image : var.frontend_image,
      essential : true,
      portMappings : [
        {
          containerPort : 3000,
          hostPort : 0,
          protocol : "tcp"
        }
      ]
    }
  ])

  cpu          = 1024
  memory       = 800
  network_mode = "bridge"
}

resource "aws_ecs_service" "teletracker_qa_frontend" {
  name            = "teletracker-qa-frontend_v2"
  cluster         = aws_ecs_cluster.teletracker-qa.id
  task_definition = aws_ecs_task_definition.frontend.arn
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

  load_balancer {
    target_group_arn = aws_lb_target_group.teletracker_qa_frontend.arn
    container_name   = "teletracker-frontend"
    container_port   = 3000
  }

  lifecycle {
    ignore_changes = [desired_count]
  }

  placement_constraints {
    expression = "attribute:purpose == server"
    type       = "memberOf"
  }
}

resource "aws_cloudfront_distribution" "teletracker-frontend" {
  provider = aws.us-east-1

  depends_on = [aws_s3_bucket.teletracker-frontend-artifacts]

  origin {
    domain_name = aws_s3_bucket.teletracker-frontend-artifacts.bucket_regional_domain_name
    origin_id   = "staticAssets"
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.teletracker-frontend.cloudfront_access_identity_path
    }
  }

  origin {
    domain_name = "lb.qa.teletracker.tv"
    origin_id   = "ELB-teletracker-qa-765186208/app"

    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_keepalive_timeout = 5
      origin_read_timeout      = 30
      origin_protocol_policy   = "https-only"
      origin_ssl_protocols     = ["TLSv1", "TLSv1.1", "TLSv1.2"]
    }
  }

  enabled         = true
  is_ipv6_enabled = true
  aliases         = ["ssr.qa.teletracker.tv", "qa.teletracker.tv"]

  viewer_certificate {
    acm_certificate_arn      = "arn:aws:acm:us-east-1:302782651551:certificate/0449030d-1711-4f27-9048-54a77660d9fe"
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  default_cache_behavior {
    allowed_methods        = ["HEAD", "GET"]
    cached_methods         = ["HEAD", "GET"]
    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 900   # 15 minutes
    max_ttl                = 21600 # 6 hours

    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }

    target_origin_id = "ELB-teletracker-qa-765186208/app"
  }

  ordered_cache_behavior {
    path_pattern    = "favicon.ico"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern    = "images/*"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern    = "_next/*"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern    = "static/*"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }
  }
}

resource "aws_cloudfront_origin_access_identity" "teletracker-frontend" {
  comment = "access-identity-ssr.qa.teletracker.qa.s3.amazonaws.com"
}

resource "aws_cloudfront_distribution" "app" {
  provider = aws.us-east-1

  depends_on = [aws_s3_bucket.teletracker-frontend-artifacts]

  origin {
    domain_name = aws_s3_bucket.teletracker-frontend-artifacts.bucket_regional_domain_name
    origin_id   = "staticAssets"
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.teletracker-frontend.cloudfront_access_identity_path
    }
  }

  origin {
    domain_name = "lb.qa.teletracker.tv"
    origin_id   = "ELB-teletracker-qa-765186208/app"

    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_keepalive_timeout = 5
      origin_read_timeout      = 30
      origin_protocol_policy   = "https-only"
      origin_ssl_protocols     = ["TLSv1", "TLSv1.1", "TLSv1.2"]
    }
  }

  enabled         = true
  is_ipv6_enabled = true
  aliases         = ["telescopetv.com"]

  viewer_certificate {
    acm_certificate_arn      = "arn:aws:acm:us-east-1:302782651551:certificate/08536907-63a1-456f-95fd-4b41201d9319"
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  default_cache_behavior {
    allowed_methods        = ["HEAD", "GET"]
    cached_methods         = ["HEAD", "GET"]
    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 900   # 15 minutes
    max_ttl                = 21600 # 6 hours

    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }

    target_origin_id = "ELB-teletracker-qa-765186208/app"
  }

  ordered_cache_behavior {
    path_pattern    = "favicon.ico"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern    = "images/*"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern    = "_next/*"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }
  }

  ordered_cache_behavior {
    path_pattern    = "static/*"
    allowed_methods = ["HEAD", "GET"]
    cached_methods  = ["HEAD", "GET"]

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000

    target_origin_id = "staticAssets"

    forwarded_values {
      query_string = true

      cookies {
        forward = "all"
      }
    }
  }
}

resource "aws_cloudfront_origin_access_identity" "app" {
  comment = "access-identity-ssr.qa.teletracker.qa.s3.amazonaws.com"
}