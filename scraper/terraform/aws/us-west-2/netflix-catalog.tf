variable "netflix_catalog_crawler_version" {
  type = string
}

module "netflix_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.netflix_catalog_crawler_version

  name = "netflix_catalog_crawler"
  spider_name = "netflix"
  output_path = "s3://${data.aws_s3_bucket.data_bucket.id}/scrape-results/netflix/catalog/{date}/items_{time}.jl"

  # Every monday and the first of the month
  schedule = ["cron(0 7 ? * */3 *)", "cron(0 7 1 * ? *)"]
}
