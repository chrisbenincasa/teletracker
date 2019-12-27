resource "aws_codebuild_project" "scraper-codebuild" {
  name         = "Scrapers-Build-Deploy"
  service_role = "arn:aws:iam::302782651551:role/service-role/ScraperCodeBuildRole"

  artifacts {
    type           = "S3"
    location       = "teletracker-artifacts"
    name           = "scrapers-codebuild"
    packaging      = "NONE"
    namespace_type = "NONE"
  }

  environment {
    compute_type                = "BUILD_GENERAL1_SMALL"
    image                       = "aws/codebuild/standard:3.0"
    type                        = "LINUX_CONTAINER"
    privileged_mode             = false
    image_pull_credentials_type = "CODEBUILD"
  }

  source {
    type                = "GITHUB"
    location            = "https://github.com/chrisbenincasa/teletracker.git"
    insecure_ssl        = false
    report_build_status = false
    git_clone_depth     = 1
    buildspec           = "buildspec.scrapers.yml"
  }

  logs_config {
    cloudwatch_logs {
      group_name = "codebuild-logs"
      status     = "ENABLED"
    }

    s3_logs {
      encryption_disabled = false
      status              = "DISABLED"
    }
  }
}

resource "aws_codebuild_project" "server-codebuild" {
  name         = "Server-Build"
  service_role = "arn:aws:iam::302782651551:role/ServerCodeBuildRole"

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type                = "BUILD_GENERAL1_SMALL"
    image                       = "302782651551.dkr.ecr.us-west-1.amazonaws.com/teletracker-build/server:latest"
    type                        = "LINUX_CONTAINER"
    privileged_mode             = true
    image_pull_credentials_type = "SERVICE_ROLE"
  }

  source {
    type                = "GITHUB"
    location            = "https://github.com/chrisbenincasa/teletracker.git"
    insecure_ssl        = false
    report_build_status = false
    git_clone_depth     = 1
    buildspec           = "buildspec.server.yml"
  }

  logs_config {
    cloudwatch_logs {
      group_name = "codebuild-logs"
      status     = "ENABLED"
    }

    s3_logs {
      encryption_disabled = false
      status              = "DISABLED"
    }
  }
}