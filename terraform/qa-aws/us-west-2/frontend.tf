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

resource "aws_lambda_function" "teletracker-frontend-lambda" {
  provider = "aws.us-east-1"

  description   = "Handles SSR requests for Teletracker frontend"
  function_name = "teletracker-frontend-ssr"
  handler       = "index.handler"
  runtime       = "nodejs12.x"

  s3_bucket         = data.aws_s3_bucket_object.teletracker-frontend-lambda-zip.bucket
  s3_key            = data.aws_s3_bucket_object.teletracker-frontend-lambda-zip.key
  s3_object_version = data.aws_s3_bucket_object.teletracker-frontend-lambda-zip.version_id

  role = aws_iam_role.teletracker-frontend-lambda-role.arn

  publish = true

  timeout     = 30
  memory_size = 1024
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
        aws_lambda_function.teletracker-frontend-lambda.role
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

resource "aws_cloudfront_distribution" "teletracker-frontend" {
  provider = "aws.us-east-1"

  depends_on = [aws_s3_bucket.teletracker-frontend-artifacts]

  origin {
    domain_name = aws_s3_bucket.teletracker-frontend-artifacts.bucket_regional_domain_name
    origin_id   = "staticAssets"
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.teletracker-frontend.cloudfront_access_identity_path
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

    lambda_function_association {
      lambda_arn   = aws_lambda_function.teletracker-frontend-lambda.qualified_arn
      event_type   = "origin-request"
      include_body = false
    }

    target_origin_id = "staticAssets"
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
