variable "showtime_catalog_crawler_version" {
  type = string
}

module "showtime_catalog_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.disneyplus_catalog_crawler_version

  name        = "showtime_catalog_crawler"
  spider_name = "showtime"

  outputs = [
    "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/showtime/catalog/{date}/items_{time}.jl:jl",
    "sqs://${replace(data.aws_sqs_queue.scrape_item_output_queue.id, "https://", "")}:sqs"
  ]

  dynamodb_output_table = aws_dynamodb_table.crawls.name

  # Every monday and the first of the month
  schedule = [
    "cron(0 7 ? * */3 *)",
  "cron(0 7 1 * ? *)"]

  gen_service = false
}
