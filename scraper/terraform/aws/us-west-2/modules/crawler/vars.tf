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

variable "output_path" {
  type = string
}

variable "output_format" {
  type = string
  default = "jl"
}

variable "schedule" {
  type = list
  default = []
}