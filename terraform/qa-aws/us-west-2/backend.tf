terraform {
  backend "s3" {
    bucket = "teletracker-terraform-qa"
    key    = "terraform/us-west-2/state"
    region = "us-west-1"
  }
}
