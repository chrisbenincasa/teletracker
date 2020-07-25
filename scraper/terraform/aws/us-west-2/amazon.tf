variable "amazon_catalog_crawler_version" {
  type = string
}

module "amazon_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.amazon_catalog_crawler_version

  name        = "amazon_crawler"
  spider_name = "amazon"

  outputs = [
    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/amazon/catalog/{date}/items_{time}.jl:jl",
    "sqs://${replace(data.aws_sqs_queue.scrape_item_output_queue.id, "https://", "")}:sqs"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name

  schedule = []

  gen_service = false
}