data "aws_ecs_cluster" "ecs-cluster" {
  cluster_name = "teletracker-qa"
}

data "aws_ecs_service" "ecs-service" {
  cluster_arn = data.aws_ecs_cluster.ecs-cluster.arn
  service_name = "teletracker-qa"
}