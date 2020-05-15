variable "server_image" {
  type = string
}

variable "task_consumer_image" {
  type = string
}

variable "es_ingest_image" {
  type = string
}

variable "es_item_denorm_image" {
  type = string
}

variable "scraper-s3-bucket" {
  type    = string
  default = "us-west-2-teletracker-artifacts"
}