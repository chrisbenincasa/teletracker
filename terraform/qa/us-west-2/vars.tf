variable "server_image" {
  type = string
}

variable "consumer_image" {
  type = string
}

variable "scraper-s3-bucket" {
  type    = string
  default = "us-west-2-teletracker-artifacts"
}

//variable "datadog_api_key" {
//  type = string
//}