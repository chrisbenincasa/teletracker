variable "project_id" {
  type    = string
  default = "teletracker"
}

variable "trigger_name" {
  type = string
}

variable "function_name" {
  type = string
}

variable "bucket_name" {
  type    = string
  default = "teletracker-build-artifacts"
}

variable "cron_schedule" {
  type    = string
  default = "0 2 * * *"
}

variable "time_zone" {
  type    = string
  default = "Etc/UTC"
}


variable "function_version" {
  type = string
}


variable "entrypoint" {
  type = string
}

variable "extra_env_vars" {
  type    = map
  default = {}
}

variable "timeout" {
  type    = string
  default = "60"
}

variable "event_data" {
  type = map
  default = {
    "default" = "run"
  }
}

variable "memory_mb" {
  type    = number
  default = 256
}
