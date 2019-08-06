terraform {
  backend "gcs" {
    bucket = "teletracker-terraform-qa"
    prefix = "terraform/state"
  }
}