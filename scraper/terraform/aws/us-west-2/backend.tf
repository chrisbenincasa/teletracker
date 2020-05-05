terraform {
  backend "s3" {
    bucket = "teletracker-terraform-qa"
    key    = "terraform/us-west-2/scrapers-state"
    region = "us-west-1"
  }
}
