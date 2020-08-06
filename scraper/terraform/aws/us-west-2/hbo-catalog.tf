variable "hbo_catalog_crawler_version" {
  type = string
}

variable "hbo_max_crawler_version" {
  type = string
}

module "hbo_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hbo_catalog_crawler_version

  name        = "hbo_catalog_crawler"
  spider_name = "hbo_go_catalog"
  cluster_name = data.aws_ecs_cluster.ecs-cluster.cluster_name

  outputs = [
    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/hbo/catalog/{date}/items_{time}.jl:jl",
    "sqs://${replace(data.aws_sqs_queue.scrape_item_output_queue.id, "https://", "")}:sqs"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name

  # Every 3rd day and the first of the month
  schedule = [
    "cron(0 7 ? * */3 *)",
  "cron(0 7 1 * ? *)"]

  gen_service = false
}

module "hbo_changes_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hbo_catalog_crawler_version

  name        = "hbo_changes_crawler"
  spider_name = "hbo_changes"
  cluster_name = data.aws_ecs_cluster.ecs-cluster.cluster_name

  outputs = [
  "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/hbo/changes/{date}/items_{time}.jl:jl"]

  dynamodb_output_table = aws_dynamodb_table.crawls.name

  # Every 3rd day and the first of the month
  schedule = [
    "cron(0 7 ? * */3 *)",
  "cron(0 7 1 * ? *)"]

  gen_service = false
}

module "hbo_max_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hbo_max_crawler_version

  name        = "hbo_max_crawler"
  spider_name = "hbo_max_authenticated"
  cluster_name = data.aws_ecs_cluster.ecs-cluster.cluster_name

  outputs = [
    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/hbo-max/catalog/{date}/items_{time}.jl:jl",
    "sqs://${replace(data.aws_sqs_queue.scrape_item_output_queue.id, "https://", "")}:sqs"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name

  # Every 3rd day and the first of the month
  schedule = [
    "cron(0 7 ? * */3 *)",
  "cron(0 7 1 * ? *)"]

  gen_service = false
}
