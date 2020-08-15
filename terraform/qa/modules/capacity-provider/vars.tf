variable "service_name" {
  type = string
}

variable "user_data" {
  type = string
}

variable "snapshot_id" {
  type = string
}

variable "security_groups" {
  type = list(string)
  default = []
}

variable "network_interface_id" {
  type = string
  default = ""
}

variable "iam_instance_profile" {
  type = string
}

variable "tags" {
  type = map(string)
  default = {}
}

variable "min" {
  type = number
  default = 0
}

variable "max" {
  type = number
  default = 1
}

variable "desired" {
  type = number
  default = 1
}

variable "vpc_id" {
  type = string
}