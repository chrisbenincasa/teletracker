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

variable "output_format" {
  type = string
  default = "jl"
}

variable "s3_directory" {
  type = string
}

variable "s3_path" {
  type = string
}