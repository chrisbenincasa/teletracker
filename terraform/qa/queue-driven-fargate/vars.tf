variable "image" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "cluster_id" {
  type = string
}

variable "service_name" {
  type = string
}

variable "consumer_mode" {
  type = string
}

variable "queue_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "fifo" {
  type = bool
  default = true
}

variable "content_based_dedupe" {
  type = bool
  default = true
}

variable "cpu" {
  type = number
  default = 512
}

variable "memory" {
  type = number
  default = 1024
}