terraform {
  backend "s3" {
    bucket = "teletracker-terraform-qa"
    key    = "terraform/state"
    region = "us-west-1"
  }
}
