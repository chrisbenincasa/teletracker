module "es-ingest-consumer" {
  source = "../queue-driven-fargate"

  cluster_id    = aws_ecs_cluster.teletracker-qa.id
  cluster_name  = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "EsIngestConsumer"
  image         = var.es_ingest_image
  queue_name    = "teletracker-es-ingest-qa"
  service_name  = "es-ingest-consumer"
  vpc_id        = data.aws_vpc.teletracker-qa-vpc.id

  content_based_dedupe = false
}

module "es-item-denorm-consumer" {
  source = "../queue-driven-fargate"

  cluster_id    = aws_ecs_cluster.teletracker-qa.id
  cluster_name  = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "EsItemDenormalizeConsumer"
  image         = var.es_item_denorm_image
  queue_name    = "teletracker-es-item-denormalization-qa"
  service_name  = "es-item-denorm-consumer"
  vpc_id        = data.aws_vpc.teletracker-qa-vpc.id

  content_based_dedupe = false
}

module "es-person-denorm-consumer" {
  source = "../queue-driven-fargate"

  cluster_id    = aws_ecs_cluster.teletracker-qa.id
  cluster_name  = aws_ecs_cluster.teletracker-qa.name
  consumer_mode = "EsPersonDenormalizeConsumer"
  image         = var.es_person_denorm_image
  queue_name    = "teletracker-es-person-denormalization-qa"
  service_name  = "es-person-denorm-consumer"
  vpc_id        = data.aws_vpc.teletracker-qa-vpc.id

  content_based_dedupe = false
}

data "template_file" "es_ingest_consumer_user_data" {
  template = file("${path.module}/files/t3a-template-user-data.txt")
  vars = {
    cluster = local.teletracker_qa_cluster_name
    purpose = "es-ingest"
  }
}

module "es_ingest_capacity_provider" {
  source = "../modules/capacity-provider"

  service_name         = "es-ingest-consumer_v2"
  iam_instance_profile = data.aws_iam_instance_profile.ecs-instance-profile.arn
  snapshot_id          = "snap-0f6cf4b4deae5f20a"
  user_data            = data.template_file.es_ingest_consumer_user_data.rendered
  vpc_id               = data.aws_vpc.teletracker-qa-vpc.id
  security_groups      = ["sg-0274044ba76f52e41", aws_security_group.ssh_access.id]
}

module "es_ingest_consumer_ec2" {
  source = "../modules/queue-driven-asg"

  cluster_name      = local.teletracker_qa_cluster_name
  consumer_mode     = "EsIngestConsumer"
  image             = var.es_ingest_image
  queue_name        = "teletracker-es-ingest-qa"
  service_name      = "es-ingest-consumer_v2"
  vpc_id            = data.aws_vpc.teletracker-qa-vpc.id
  capacity_provider = module.es_ingest_capacity_provider.capacity_provider_name
}