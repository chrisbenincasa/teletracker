variable "project_id" {
  type    = "string"
  default = "teletracker"
}

variable "region" {
  type    = "string"
  default = "us-east1"
}

variable "compute_service_account" {
  type    = "string"
  default = "558300338939-compute@developer.gserviceaccount.com"
}

variable "additional_metadata" {
  type        = "map"
  description = "Additional metadata to attach to the instance"
  default     = {}
}

variable "env" {
  type    = "string"
  default = "qa"
}

variable "image" {
  type        = "string"
  description = "The image string of the currently deployed Teletracker server image"
}

variable "consumer_image" {
  type        = "string"
  description = "The image string of the currently deployed Teletracker consumer image"
}
