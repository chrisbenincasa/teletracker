variable "name" {
  type = string
}

variable "spider_name" {
  type = string
}

variable "crawler_image" {
  type = string
}

variable "image_version" {
  type = string
}

variable "outputs" {
  type = list(string)
}

variable "schedule" {
  type = list(string)
  default = []
}

variable "dynamodb_output_table" {
  type = string
}

variable "gen_service" {
  type    = bool
  default = true
}