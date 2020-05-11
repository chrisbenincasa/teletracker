variable "hulu_catalog_crawler_version" {
  type = string
}

module "hulu_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hulu_catalog_crawler_version

  name = "hulu_catalog_crawler"
  spider_name = "hulu"
  s3_directory = "hulu"
  s3_path = "direct/items.jsonlines"
}
