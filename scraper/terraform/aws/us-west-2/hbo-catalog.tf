variable "hbo_catalog_crawler_version" {
  type = string
}

module "hbo_crawler" {
  source = "./modules/crawler"

  crawler_image = var.crawler_image
  image_version = var.hbo_catalog_crawler_version

  name = "hbo_catalog_crawler"
  spider_name = "hbo"
  s3_directory = "hbo"
  s3_path = "catalog/items.jsonlines"
}
