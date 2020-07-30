resource "aws_elasticache_cluster" "crawl_store" {
  count = 1

  cluster_id = "crawler-store"
  engine = "redis"
  engine_version = "5.0.6"
  node_type = "cache.t3.micro"
  num_cache_nodes = 1

  security_group_ids = [
    aws_security_group.crawl_store_sg.id]
  subnet_group_name = aws_elasticache_subnet_group.crawl_store_subnet_group.name
}

resource "aws_elasticache_subnet_group" "crawl_store_subnet_group" {
  name = "crawl-store-cache-subnet"
  subnet_ids = data.aws_subnet_ids.teletracker-subnet-ids.ids
}

data "aws_route53_zone" "teletracker-tv-zone" {
  name = "teletracker.tv"
}

resource "aws_route53_record" "crawl_store_dns_record" {
  name = "crawl_store.cache.internal.qa.teletracker.tv"
  type = "CNAME"
  zone_id = data.aws_route53_zone.teletracker-tv-zone.zone_id
  ttl = 60

  records = [
    aws_elasticache_cluster.crawl_store[0].cache_nodes[0].address]
}

resource "aws_security_group" "crawl_store_sg" {
  name = "Crawl Store Cache"
  description = "Group for the Crawl Store cache cluster"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  ingress {
    from_port = 6379
    to_port = 6379
    protocol = "TCP"
    security_groups = [
      module.amazon_crawler.crawler_sg_id
    ]
    cidr_blocks = [
      # Christian's computer
      "67.164.191.249/32"
    ]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = [
      "0.0.0.0/0"]
    ipv6_cidr_blocks = [
      "::/0"]
  }
}