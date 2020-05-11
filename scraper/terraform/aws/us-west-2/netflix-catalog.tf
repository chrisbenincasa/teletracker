variable "netflix_catalog_crawler_version" {
  type = string
}

module "netflix_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.netflix_catalog_crawler_version

  name = "netflix_catalog_crawler"
  spider_name = "netflix_spider"
  s3_directory = "netflix/direct"
  s3_path = "items.jsonlines"
}
