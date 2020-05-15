module "es-ingest-consumer" {
  source = "../queue-driven-fargate"

  cluster_id = aws_ecs_cluster.teletracker-qa.id
  cluster_name = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "EsIngestConsumer"
  image = var.es_ingest_image
  queue_name = "teletracker-es-ingest-qa"
  service_name = "es-ingest-consumer"
  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  content_based_dedupe = false
}

module "es-item-denorm-consumer" {
  source = "../queue-driven-fargate"

  cluster_id = aws_ecs_cluster.teletracker-qa.id
  cluster_name = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "EsItemDenormalizeConsumer"
  image = var.es_item_denorm_image
  queue_name = "teletracker-es-item-denormalization-qa"
  service_name = "es-item-denorm-consumer"
  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  content_based_dedupe = false
}