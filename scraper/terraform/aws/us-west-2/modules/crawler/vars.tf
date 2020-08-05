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
  type    = list(string)
  default = []
}

variable "dynamodb_output_table" {
  type = string
}

variable "gen_service" {
  type    = bool
  default = false
}

variable "gen_scaling" {
  type    = bool
  default = false
}

variable "redis_ping_schedule" {
  type = string
  # Every 10 minutes, creates roughly 4k metrics a month
  default = "*/10 * * * ? *"
}

variable "max_spider_count" {
  type    = number
  default = 3
}

variable "redis_host" {
  type    = string
  default = ""
}

variable "extra_args" {
  type    = list(string)
  default = []
}

variable "scheduled_task_count" {
  type    = number
  default = 1
}

variable "cpu" {
  type    = number
  default = 256
}

variable "memory" {
  type    = number
  default = 512
}