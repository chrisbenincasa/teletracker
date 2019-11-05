//
// Module: aws-cloudwatch
//

variable name {
  description = "The auto scaling action name"
}

variable description {
  description = "The auto scaling action description"
  default     = ""
}

variable comparison_operator {
  description = "The arithmetic operation to use when comparing the specified Statistic and Threshold. The specified Statistic value is used as the first operand. Either of the following is supported: GreaterThanOrEqualToThreshold, GreaterThanThreshold, LessThanThreshold, LessThanOrEqualToThreshold."
}

variable "lower_bound" {
  description = "Set this to 0 if your comparison_operator is GreaterThan otherwise leave unset.  Either lower or upper has to be set"
  default     = ""
}

variable "upper_bound" {
  description = "Set this to 0 if your comparison_operator is LessThan otherwise leave unset. Either lower or upper has to be set"
  default     = ""
}

variable "scaling_adjustment" {
  description = "What is the amount to scale by. For example if the adjustment type is PercentChangeInCapacity and this value is set to 10, the alarm will scale by 10%"
}

variable evaluation_periods {
  description = "The number of periods over which data is compared to the specified threshold."
  default     = 3
}

variable "scaling_dimension" {
  description = "The dimension to scale"
  default     = "ecs:service:DesiredCount"
}

variable "service_namespace" {
  default     = "ecs"
  description = "Either ecs or ec2"
}

variable metric_name {
  description = "The name for the alarm's associated metric"
}

variable "queue_name" {
  description = "The queue name to watch"
}

variable namespace {
  description = "The namespace for the alarm's associated metric (https://docs.aws.amazon.com/AmazonCloudWatch/latest/DeveloperGuide/aws-namespaces.html)"
  default     = "AWS/SQS"
}

variable period_seconds {
  description = "The period in seconds over which the specified statistic is applied."
  default     = 60
}

variable statistic {
  description = " (Optional) The statistic to apply to the alarm's associated metric. Either of the following is supported: SampleCount, Average, Sum, Minimum, Maximum"
  default     = "Average"
}

variable metric_aggregation {
  description = "The statistic to apply to the alarm's associated metric. Either of the following is supported: Average, Minimum, Maximum"
  default     = "Average"
}

variable threshold {
  description = "The value against which the specified statistic is compared."
}

variable "adjustment_type" {
  description = "The adjustment type: ChangeInCapacity, ExactCapacity, PercentChangeInCapacity"
}

variable "cooldown_seconds" {
  description = "The amount of time, in seconds, after a scaling activity completes and before the next scaling activity can start."
  default     = 300
}

variable "ecs_target_resource_id" {
  description = "The ecs resource id to affect. Of the form service/{cluster}/{service-name}"
}

variable "service_name" {
  description = "Service name to scale"
}

variable "cluster_name" {
  description = "The cluster the service is on"
}

variable "region" {
  description = "The AWS region where the task should live"
}

variable "environment" {
  description = "The AWS envrionment where the task should live"
}

variable "shared_credentials_file" {
  default = "~/.aws/credentials"
}
