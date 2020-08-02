data "aws_ssm_parameter" "github_oauth_token" {
  name            = "github-oauth-token-qa"
  with_decryption = true
}

locals {
  log_stream_name = "codebuild-logs"
}

data "aws_iam_policy_document" "crawlers_pipeline_assume_role_doc" {
  statement {
    effect = "Allow"
    actions = [
    "sts:AssumeRole"]
    principals {
      identifiers = [
      "codepipeline.amazonaws.com"]
      type = "Service"
    }
  }
}

data "aws_iam_policy_document" "crawlers_pipeline_policy_doc" {
  statement {
    effect = "Allow"
    actions = [
    "sts:AssumeRole"]
    resources = [
      aws_iam_role.crawler_build_role.arn
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:*"
    ]
    resources = [
      "arn:aws:s3:::codepipeline-us-west-2-*",
      "arn:aws:s3:::codepipeline-us-west-1-*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "codestar-connections:UseConnection"
    ]
    resources = [
      "*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "codebuild:BatchGetBuilds",
      "codebuild:StartBuild",
      "codebuild:BatchGetBuildBatches",
    "codebuild:StartBuildBatch"]
    resources = [
    "*"]
  }

  statement {
    effect = "Allow"
    actions = [
    "ecr:DescribeImages"]
    resources = [
    "*"]
  }

  statement {
    effect = "Allow"
    actions = [
      "states:DescribeExecution",
      "states:DescribeStateMachine",
    "states:StartExecution"]
    resources = [
    "*"]
  }
}

resource "aws_iam_policy" "crawlers_pipeline_policy" {
  policy = data.aws_iam_policy_document.crawlers_pipeline_policy_doc.json
}

resource "aws_iam_role" "crawlers_pipeline_role" {
  name               = "Crawlers-Pipeline-Role"
  assume_role_policy = data.aws_iam_policy_document.crawlers_pipeline_assume_role_doc.json
}

resource "aws_iam_role_policy_attachment" "crawlers_pipeline_policy_attachment" {
  policy_arn = aws_iam_policy.crawlers_pipeline_policy.arn
  role       = aws_iam_role.crawlers_pipeline_role.name
}

//resource "aws_codepipeline" "crawlers_pipeline" {
//  name     = "Crawlers-Pipeline"
//  role_arn = aws_iam_role.crawlers_pipeline_role.arn
//
//  artifact_store {
//    location = data.aws_s3_bucket.artifact_bucket.bucket
//    type     = "S3"
//  }
//
//  stage {
//    name = "Source"
//
//    action {
//      category = "Source"
//      name     = "Source"
//      owner    = "ThirdParty"
//      provider = "GitHub"
//      version  = "1"
//
//      output_artifacts = [
//      "source_output"]
//
//      configuration = {
//        Owner      = "chrisbenincasa"
//        Repo       = "teletracker"
//        Branch     = "master"
//        OAuthToken = data.aws_ssm_parameter.github_oauth_token.value
//      }
//    }
//  }
//
//  stage {
//    name = "Build"
//    action {
//      category = "Build"
//      name     = "Build"
//      owner    = "AWS"
//      provider = "CodeBuild"
//      version  = "1"
//
//      configuration = {
//        ProjectName = aws_codebuild_project.crawlers_codebuild.name
//      }
//
//      input_artifacts = [
//      "source_output"]
//
//      output_artifacts = [
//      "build_output"]
//    }
//  }
//
//  stage {
//    name = "Approval"
//    action {
//      category = "Approval"
//      name     = "Approval"
//      owner    = "AWS"
//      provider = "Manual"
//      version  = "1"
//    }
//  }
//
//  stage {
//    name = "Deploy"
//    action {
//      category = "Build"
//      name     = "Deploy"
//      owner    = "AWS"
//      provider = "CodeBuild"
//      version  = "1"
//
//      configuration = {
//        ProjectName = aws_codebuild_project.deploy_crawlers_codebuild.name
//      }
//
//      input_artifacts = [
//      "build_output"]
//    }
//  }
//}

data "aws_iam_policy_document" "crawls_service_role_policy_doc" {
  statement {
    effect = "Allow"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]

    resources = [
      "arn:aws:logs:${data.aws_region.current.name}:302782651551:log-group:${local.log_stream_name}:log-stream:*",
      "arn:aws:logs:${data.aws_region.current.name}:302782651551:log-group:${local.log_stream_name}"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "s3:*"
    ]
    resources = [
      "arn:aws:s3:::codepipeline-us-west-2-*",
      "arn:aws:s3:::codepipeline-us-west-1-*",
      "arn:aws:s3:::${data.aws_s3_bucket.artifact_bucket.bucket}/*"
    ]
  }

  statement {
    effect = "Allow"
    actions = [
      "ecr:*"
    ]
    resources = [
    "*"]
  }
}

data "aws_iam_policy_document" "crawls_assume_role_policy_doc" {
  statement {
    effect = "Allow"
    actions = [
    "sts:AssumeRole"]
    principals {
      identifiers = [
      "codebuild.amazonaws.com"]
      type = "Service"
    }
    sid = ""
  }
}

resource "aws_iam_policy" "crawls_build_iam_policy" {
  name   = "Crawls-Build-Policy"
  policy = data.aws_iam_policy_document.crawls_service_role_policy_doc.json
}

resource "aws_iam_role" "crawler_build_role" {
  name               = "Crawlers-Build-Role"
  assume_role_policy = data.aws_iam_policy_document.crawls_assume_role_policy_doc.json
}

resource "aws_iam_role_policy_attachment" "crawler_build_policy_attachment" {
  policy_arn = aws_iam_policy.crawls_build_iam_policy.arn
  role       = aws_iam_role.crawler_build_role.name
}

resource "aws_codebuild_project" "crawlers_codebuild" {
  name         = "Crawlers-Build"
  service_role = aws_iam_role.crawler_build_role.arn

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type    = "BUILD_GENERAL1_SMALL"
    image           = "aws/codebuild/standard:3.0"
    type            = "LINUX_CONTAINER"
    privileged_mode = true
  }

  source {
    type                = "GITHUB"
    location            = "https://github.com/chrisbenincasa/teletracker.git"
    insecure_ssl        = false
    report_build_status = false
    git_clone_depth     = 1
    buildspec           = "scraper/buildspec.yml"
  }

  logs_config {
    cloudwatch_logs {
      group_name = local.log_stream_name
      status     = "ENABLED"
    }

    s3_logs {
      encryption_disabled = false
      status              = "DISABLED"
    }
  }
}

resource "aws_codebuild_project" "deploy_crawlers_codebuild" {
  name         = "Crawlers-Deploy"
  service_role = aws_iam_role.crawler_build_role.arn

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type    = "BUILD_GENERAL1_SMALL"
    image           = "aws/codebuild/standard:3.0"
    type            = "LINUX_CONTAINER"
    privileged_mode = true
  }

  source {
    type                = "GITHUB"
    location            = "https://github.com/chrisbenincasa/teletracker.git"
    insecure_ssl        = false
    report_build_status = false
    git_clone_depth     = 1
    buildspec           = "scraper/buildspec.deploy.yml"
  }

  logs_config {
    cloudwatch_logs {
      group_name = local.log_stream_name
      status     = "ENABLED"
    }

    s3_logs {
      encryption_disabled = false
      status              = "DISABLED"
    }
  }
}

//resource "aws_codepipeline_webhook" "crawlers_aws_webhook" {
//  name = "crawlers-filtered-webhook"
//  authentication = "GITHUB_HMAC"
//  target_action = "Source"
//  target_pipeline = aws_codepipeline.crawlers_pipeline.name
//
//  filter {
//    json_path = "$.commits[*]['added', 'removed', 'modified'].[?(@ =~ /^scraper\/.*/)]"
//    match_equals = ""
//  }
//}