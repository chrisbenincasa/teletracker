resource "aws_iam_policy" "kms_encrypt_decrypt_policy" {
  name        = "KMS_Encrypt_Decrypt"
  path        = "/"
  description = "Allows for KMS Encrypt and Decrypt using all CMK"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": {
    "Effect": "Allow",
    "Action": [
      "kms:Encrypt",
      "kms:Decrypt"
    ],
    "Resource": [
      "arn:aws:kms:*:302782651551:key/*"
    ]
  }
}
EOF
}

resource "aws_iam_policy" "lambda_execute" {
  name        = "Lambda_Execute"
  path        = "/"
  description = "Ability to trigger Lambdas"

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": {
    "Effect": "Allow",
    "Action": [
      "lambda:InvokeFunction"
    ],
    "Resource": [
      "arn:aws:lambda:*:302782651551:function:*"
    ]
  }
}
EOF
}

resource "aws_iam_role" "cloudbuild-terraform-role" {
  name               = "CloudbuildTerraformRole"
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "codebuild.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

resource "aws_iam_role_policy_attachment" "cloudbuild-terraform-s3-policy" {
  policy_arn = data.aws_iam_policy.s3_full_access_policy.arn
  role       = aws_iam_role.cloudbuild-terraform-role.name
}

data "aws_iam_policy" "ssm_read_only_policy" {
  arn = "arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess"
}

data "aws_iam_policy" "kms_power_user_policy" {
  arn = "arn:aws:iam::aws:policy/AWSKeyManagementServicePowerUser"
}

data "aws_iam_policy" "lambda_basic_execution" {
  arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_policy" "datadog_collect_policy" {
  name   = "DatadogDataCollect"
  policy = <<EOF
{
  "Statement": [
    {
      "Action": [
        "apigateway:GET",
        "autoscaling:Describe*",
        "budgets:ViewBudget",
        "cloudfront:GetDistributionConfig",
        "cloudfront:ListDistributions",
        "cloudtrail:DescribeTrails",
        "cloudtrail:GetTrailStatus",
        "cloudwatch:Describe*",
        "cloudwatch:Get*",
        "cloudwatch:List*",
        "codedeploy:List*",
        "codedeploy:BatchGet*",
        "directconnect:Describe*",
        "dynamodb:List*",
        "dynamodb:Describe*",
        "ec2:Describe*",
        "ecs:Describe*",
        "ecs:List*",
        "elasticache:Describe*",
        "elasticache:List*",
        "elasticfilesystem:DescribeFileSystems",
        "elasticfilesystem:DescribeTags",
        "elasticloadbalancing:Describe*",
        "elasticmapreduce:List*",
        "elasticmapreduce:Describe*",
        "es:ListTags",
        "es:ListDomainNames",
        "es:DescribeElasticsearchDomains",
        "health:DescribeEvents",
        "health:DescribeEventDetails",
        "health:DescribeAffectedEntities",
        "kinesis:List*",
        "kinesis:Describe*",
        "lambda:AddPermission",
        "lambda:GetPolicy",
        "lambda:List*",
        "lambda:RemovePermission",
        "logs:TestMetricFilter",
        "logs:PutSubscriptionFilter",
        "logs:DeleteSubscriptionFilter",
        "logs:DescribeSubscriptionFilters",
        "rds:Describe*",
        "rds:List*",
        "redshift:DescribeClusters",
        "redshift:DescribeLoggingStatus",
        "route53:List*",
        "s3:GetBucketLogging",
        "s3:GetBucketLocation",
        "s3:GetBucketNotification",
        "s3:GetBucketTagging",
        "s3:ListAllMyBuckets",
        "s3:PutBucketNotification",
        "ses:Get*",
        "sns:List*",
        "sns:Publish",
        "sqs:ListQueues",
        "states:ListStateMachines",
        "states:DescribeStateMachine",
        "support:*",
        "tag:GetResources",
        "tag:GetTagKeys",
        "tag:GetTagValues",
        "xray:BatchGetTraces",
        "xray:GetTraceSummaries"
      ],
      "Effect": "Allow",
      "Resource": "*"
    }
  ],
  "Version": "2012-10-17"
}
EOF
}

resource "aws_iam_role" "datadog-collect-role" {
  name        = "DatadogCollectRole"
  description = "Allows read-only access by datadog to collect metrics."

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Condition": {},
      "Principal": {
        "AWS": "arn:aws:iam::464622532012:root"
      },
      "Effect": "Allow"
    }
  ]
}
EOF
}

//resource "aws_iam_role_policy_attachment" "cloudbuild-terraform-s3-policy" {
//  policy_arn = data.aws_iam_policy.sqs_full_access_policy.arn
//  role       = aws_iam_role.cloudbuild-terraform-role.name
//}
