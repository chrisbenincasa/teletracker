provider "aws" {
  version = "~> 2.0"
  region  = "us-west-1"
}

provider "aws" {
  version = "~> 2.0"
  alias   = "us-west-2"
  region  = "us-west-2"
}

provider "aws" {
  version = "~> 2.0"
  alias   = "us-east-1"
  region  = "us-east-1"
}
