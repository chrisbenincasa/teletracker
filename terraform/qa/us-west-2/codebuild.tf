resource "aws_codebuild_project" "server-codebuild" {
  name         = "Server-Build"
  service_role = "arn:aws:iam::302782651551:role/ServerCodeBuildRole"

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
    buildspec           = "codebuild/buildspec.server.yml"
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

//resource "aws_codebuild_webhook" "server-codebuild-github-hook" {
//  project_name = aws_codebuild_project.server-codebuild.name
//
//  filter_group {
//    filter {
//      pattern = "PUSH"
//      type    = "EVENT"
//    }
//
//    filter {
//      pattern = "master"
//      type    = "HEAD_REF"
//    }
//
//    filter {
//      pattern = "backend/.*"
//      type    = "FILE_PATH"
//    }
//  }
//}

resource "aws_codebuild_project" "consumer-codebuild" {
  name         = "Consumer-Build"
  service_role = "arn:aws:iam::302782651551:role/ServerCodeBuildRole"

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
    buildspec           = "codebuild/buildspec.consumer.yml"
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

//resource "aws_codebuild_webhook" "consumer-codebuild-github-hook" {
//  project_name = aws_codebuild_project.consumer-codebuild.name
//
//  filter_group {
//    filter {
//      pattern = "PUSH"
//      type    = "EVENT"
//    }
//
//    filter {
//      pattern = "master"
//      type    = "HEAD_REF"
//    }
//
//    filter {
//      pattern = "backend/.*"
//      type    = "FILE_PATH"
//    }
//  }
//}

resource "aws_codebuild_project" "frontend-pr-codebuild" {
  name         = "Frontend-PR-Build"
  service_role = "arn:aws:iam::302782651551:role/ServerCodeBuildRole"

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
    buildspec           = "codebuild/buildspec.web.yml"
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

  cache {
    type  = "LOCAL"
    modes = ["LOCAL_CUSTOM_CACHE"]
  }
}

resource "aws_codebuild_webhook" "frontend-pr-codebuild-github-hook" {
  project_name = aws_codebuild_project.frontend-pr-codebuild.name

  filter_group {
    filter {
      pattern = "PULL_REQUEST_CREATED,PULL_REQUEST_UPDATED,PULL_REQUEST_REOPENED"
      type    = "EVENT"
    }
  }
}

resource "aws_codebuild_project" "build-image-codebuild" {
  name         = "Build-Image-Build"
  service_role = "arn:aws:iam::302782651551:role/ServerCodeBuildRole"

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
    buildspec           = "codebuild/buildspec.build-image.yml"
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

resource "aws_codebuild_project" "terraform-deploy-codebuild" {
  name         = "QA-Deploy"
  service_role = aws_iam_role.cloudbuild-terraform-role.arn

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
    buildspec           = "codebuild/buildspec.deploy.yml"
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
