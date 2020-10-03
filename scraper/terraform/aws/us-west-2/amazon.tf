variable "amazon_catalog_crawler_version" {
  type = string
}

module "amazon_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.amazon_catalog_crawler_version

  name         = "amazon_crawler"
  spider_name  = "amazon_distributed"
  cluster_name = data.aws_ecs_cluster.ecs-cluster.cluster_name

  outputs = [
    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/%(canonical_name)s/catalog/%(version)s/items_%(now)s.jl:jl",
    "sqs://${replace(data.aws_sqs_queue.scrape_item_output_queue.id, "https://", "")}:sqs"
  ]

  extra_args = [
    "-sEMPTY_RESPONSE_RECORDER_ENABLED=True",
    "-sAUTOTHROTTLE_TARGET_CONCURRENCY=8",
    "-sTELNETCONSOLE_HOST=0.0.0.0"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name
  redis_host            = "crawl_store.cache.internal.qa.teletracker.tv"

  schedule = ["cron(0 7 ? * */3 *)",
  "cron(0 7 1 * ? *)"]
  scheduled_task_count = 3

  max_spider_count = 3

  gen_service = false
  memory      = 512
  fargate     = true
}

module "amazon_single_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.amazon_catalog_crawler_version

  name         = "amazon_crawler_single"
  spider_name  = "amazon"
  cluster_name = data.aws_ecs_cluster.ecs-cluster.cluster_name

  outputs = [
    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/%(canonical_name)s/catalog/%(version)s/items_%(now)s.jl:jl"
  ]

  extra_args = [
    "-sEMPTY_RESPONSE_RECORDER_ENABLED=True",
    "-sAUTOTHROTTLE_TARGET_CONCURRENCY=8",
    "-sTELNETCONSOLE_HOST=0.0.0.0"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name
  redis_host            = "crawl_store.cache.internal.qa.teletracker.tv"

  gen_service = false
  memory      = 512
  fargate     = true
}
