module "task-consumer" {
  source = "../queue-driven-fargate"

  cluster_id = aws_ecs_cluster.teletracker-qa.id
  cluster_name = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "TaskConsumer"
  image = var.task_consumer_image
  queue_name = "teletracker-tasks-qa"
  service_name = "task-consumer"
  vpc_id = data.aws_vpc.teletracker-qa-vpc.id
}