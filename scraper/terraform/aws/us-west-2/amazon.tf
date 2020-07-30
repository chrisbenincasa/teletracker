variable "amazon_catalog_crawler_version" {
  type = string
}

module "amazon_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.amazon_catalog_crawler_version

  name        = "amazon_crawler"
  spider_name = "amazon_distributed"

  outputs = [
    //    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/amazon/catalog/{date}/items_{time}.jl:jl",
    "sqs://${replace(data.aws_sqs_queue.scrape_item_output_queue.id, "https://", "")}:sqs",
    "sqs://${replace(data.aws_sqs_queue.amazon_item_output_queue.id, "https://", "")}:sqs"
  ]

  extra_args = [
    "-sEMPTY_RESPONSE_RECORDER_ENABLED=True",
    "-sEMPTY_RESPONSE_OUTPUT_PREFIX=scrape-results/amazon/catalog/empty_results"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name
  redis_host            = length(aws_elasticache_cluster.crawl_store) == 1 ? aws_route53_record.crawl_store_dns_record.name : ""

  schedule = []
}