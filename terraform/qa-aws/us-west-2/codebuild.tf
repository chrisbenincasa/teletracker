resource "aws_codebuild_project" "scraper-codebuild" {
  name         = "Scrapers-Build-Deploy"
  service_role = "arn:aws:iam::302782651551:role/service-role/ScraperCodeBuildRole"

  artifacts {
    type           = "S3"
    location       = aws_s3_bucket.teletracker-artifacts-us-west-2.id
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

resource "aws_codebuild_webhook" "scraper-codebuild-github-hook" {
  project_name = aws_codebuild_project.scraper-codebuild.name

  filter_group {
    filter {
      pattern = "PUSH"
      type    = "EVENT"
    }

    filter {
      pattern = "master"
      type    = "HEAD_REF"
    }

    filter {
      pattern = "scraper/.*"
      type    = "FILE_PATH"
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

resource "aws_codebuild_webhook" "server-codebuild-github-hook" {
  project_name = aws_codebuild_project.server-codebuild.name

  filter_group {
    filter {
      pattern = "PUSH"
      type    = "EVENT"
    }

    filter {
      pattern = "master"
      type    = "HEAD_REF"
    }

    filter {
      pattern = "backend/.*"
      type    = "FILE_PATH"
    }
  }
}

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
    buildspec           = "buildspec.consumer.yml"
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

resource "aws_codebuild_webhook" "consumer-codebuild-github-hook" {
  project_name = aws_codebuild_project.consumer-codebuild.name

  filter_group {
    filter {
      pattern = "PUSH"
      type    = "EVENT"
    }

    filter {
      pattern = "master"
      type    = "HEAD_REF"
    }

    filter {
      pattern = "backend/.*"
      type    = "FILE_PATH"
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
    buildspec           = "buildspec.build-image.yml"
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
    buildspec           = "buildspec.deploy.yml"
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