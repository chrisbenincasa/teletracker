data "aws_iam_role" "ecs-autoscalng-role" {
  name = "AWSServiceRoleForApplicationAutoScaling_ECSService"
}

/**
 LAMBDA
*/

resource "aws_iam_role" "redis_scaling_lambda_role" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  name = "${var.name}-RedisScalingLambdaRole"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

  path = "/service-role/"
}

data "aws_iam_policy_document" "redis_scaling_policy_doc" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  statement {
    effect = "Allow"
    actions = [
    "lambda:InvokeFunction"]
    resources = [
    "${aws_lambda_function.redis_scaling_function[0].arn}*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = [
    "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "cloudwatch:PutMetricData",
      "cloudwatch:GetMetricData",
    "cloudwatch:GetMetricStatistics"]
    resources = [
    "*"]
  }
}

resource "aws_iam_policy" "redis_scaling_policy" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  name   = "${var.name}-Redis-Scaling-Policy"
  policy = data.aws_iam_policy_document.redis_scaling_policy_doc[0].json
}

resource "aws_iam_role_policy_attachment" "redis_scaling_policy_attachment" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  policy_arn = aws_iam_policy.redis_scaling_policy[0].arn
  role       = aws_iam_role.redis_scaling_lambda_role[0].name
}

data "aws_s3_bucket_object" "redis_scaling_lambda_artifact" {
  bucket = data.aws_s3_bucket.teletracker-artifacts-us-west-2.id
  key    = "lambdas/redis_crawler_scaling.zip"
}

resource "aws_lambda_function" "redis_scaling_function" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  function_name = "crawl-trigger"
  handler       = "index.handler"
  role          = aws_iam_role.redis_scaling_lambda_role[0].arn
  runtime       = "nodejs12.x"
  memory_size   = 128

  s3_bucket         = data.aws_s3_bucket.teletracker-artifacts-us-west-2.id
  s3_key            = data.aws_s3_bucket_object.redis_scaling_lambda_artifact.key
  s3_object_version = data.aws_s3_bucket_object.redis_scaling_lambda_artifact.version_id

  environment {
    variables = {
      SPIDER_KEY = var.spider_name
    }
  }
}

/**
  CLOUDWATCH LAMBDA TRIGGER
**/

resource "aws_cloudwatch_event_rule" "scraper_lambda_event_rule" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  name                = "${aws_lambda_function.redis_scaling_function[0].function_name}-trigger"
  description         = "Run ${aws_lambda_function.redis_scaling_function[0].function_name}"
  schedule_expression = "cron(${var.redis_ping_schedule})"
}

resource "aws_cloudwatch_event_target" "scraper_lambda_event_target" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  arn  = aws_lambda_function.redis_scaling_function[0].arn
  rule = element(aws_cloudwatch_event_rule.scraper_lambda_event_rule, count.index).name
}

resource "aws_lambda_permission" "scraper_lambda_event_permission" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.redis_scaling_function[0].function_name
  principal     = "events.amazonaws.com"
  source_arn    = element(aws_cloudwatch_event_rule.scraper_lambda_event_rule, count.index).arn
}

/**
  ECS AUTOSCALING
**/

resource "aws_appautoscaling_target" "ecs_scale_target" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  max_capacity       = var.max_spider_count
  min_capacity       = 0
  resource_id        = "service/${data.aws_ecs_cluster.main_cluster.cluster_name}/${var.name}"
  role_arn           = data.aws_iam_role.ecs-autoscalng-role.arn
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "ecs_scale_up_policy" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  name               = "${var.spider_name}-scale-up"
  resource_id        = aws_appautoscaling_target.ecs_scale_target[0].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_scale_target[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_scale_target[0].service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 60
    metric_aggregation_type = "Maximum"

    step_adjustment {
      metric_interval_lower_bound = 0
      scaling_adjustment          = 1
    }
  }
}

resource "aws_appautoscaling_policy" "ecs_scale_down_policy" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  name               = "${var.name}-scale-down"
  resource_id        = aws_appautoscaling_target.ecs_scale_target[0].resource_id
  scalable_dimension = aws_appautoscaling_target.ecs_scale_target[0].scalable_dimension
  service_namespace  = aws_appautoscaling_target.ecs_scale_target[0].service_namespace

  step_scaling_policy_configuration {
    adjustment_type         = "ChangeInCapacity"
    cooldown                = 300
    metric_aggregation_type = "Maximum"

    step_adjustment {
      metric_interval_upper_bound = 0
      scaling_adjustment          = -1
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "teletracker-qa-cluster-scale-up-alarm" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  alarm_name          = "${var.name}-Crawler-Scale-Up-Alarm"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  threshold           = 1

  metric_name = "outstanding_requests"
  namespace   = "Teletracker/QA/Crawlers"
  period      = "120"
  statistic   = "Maximum"

  dimensions = {
    spider = var.spider_name
  }

  alarm_actions = [
    aws_appautoscaling_policy.ecs_scale_up_policy[0].arn
  ]
}

resource "aws_cloudwatch_metric_alarm" "teletracker-qa-cluster-scale-down-alarm" {
  count = var.gen_service && var.gen_scaling ? 1 : 0

  alarm_name = "${var.name}-Crawler-Scale-Down-Alarm"

  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = 2
  threshold           = 0

  metric_name = "outstanding_requests"
  namespace   = "Teletracker/QA/Crawlers"
  period      = "120"
  statistic   = "Maximum"

  dimensions = {
    spider = var.spider_name
  }

  alarm_actions = [
    aws_appautoscaling_policy.ecs_scale_down_policy[0].arn
  ]
}
