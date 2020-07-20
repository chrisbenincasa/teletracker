module "scrape-item-consumer" {
  source = "../queue-driven-fargate"

  queue_name = "scrape-item-output-queue-qa"
  fifo       = false

  cluster_id    = aws_ecs_cluster.teletracker-qa.id
  cluster_name  = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "ScrapeItemImportConsumer"
  image         = var.scrape_item_consumer_image
  service_name  = "scrape-item-consumer"
  vpc_id        = data.aws_vpc.teletracker-qa-vpc.id
}