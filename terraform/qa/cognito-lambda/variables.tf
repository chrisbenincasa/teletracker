variable "handler_function" {
  type = string
}

variable "function_name" {
  type = string
}

variable "timeout" {
  type    = number
  default = 60
}

variable "memory" {
  type    = number
  default = 128
}

variable "extra_env_vars" {
  type    = map
  default = {}
}

variable "trigger_input" {
  type    = string
  default = "{}"
}

variable "create_default_trigger" {
  type    = bool
  default = true
}

variable "user_pool_arn" {
  type = string
}

variable "s3_bucket" {
  type = string
}