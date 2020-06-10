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
  spider_name = "hbo_catalog"
  output_path = "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/hbo/catalog/{date}/items_{time}.jl"

  # Every 3rd day and the first of the month
  schedule = ["cron(0 7 ? * */3 *)", "cron(0 7 1 * ? *)"]
}

module "hbo_changes_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hbo_catalog_crawler_version

  name        = "hbo_changes_crawler"
  spider_name = "hbo_changes"
  output_path = "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/hbo/changes/{date}/items_{time}.jl"

  # Every 3rd day and the first of the month
  schedule = ["cron(0 7 ? * */3 *)", "cron(0 7 1 * ? *)"]
}

module "hbo_max_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hbo_max_crawler_version

  name        = "hbo_max_crawler"
  spider_name = "hbomax"
  output_path = "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/hbo-max/catalog/{date}/items_{time}.jl"

  # Every 3rd day and the first of the month
  schedule = ["cron(0 7 ? * */3 *)", "cron(0 7 1 * ? *)"]
}
