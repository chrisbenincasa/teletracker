resource "aws_elasticache_cluster" "crawl_store" {
  cluster_id      = "crawler-store"
  engine          = "redis"
  engine_version  = "5.0.6"
  node_type       = "cache.t3.micro"
  num_cache_nodes = 1
}