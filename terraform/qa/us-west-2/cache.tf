data "template_file" "crawl_cache_user_data" {
  template = file("${path.module}/files/redis-instance-user-data.txt")
  vars = {
    cluster = aws_ecs_cluster.teletracker-qa.name
  }
}

resource "aws_security_group" "all_crawlers" {
  name        = "All Crawlers"
  description = "Security group that all crawlers are a part of."

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    cidr_blocks = [
    "0.0.0.0/0"]
    ipv6_cidr_blocks = [
    "::/0"]
  }
}

resource "aws_security_group" "crawler_cache" {
  name        = "Crawler Cache"
  description = "Group for the Crawler Cache ECS task"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  // Allow ingress from Public ECS services and all crawlers.
  ingress {
    protocol  = "tcp"
    from_port = 6379
    to_port   = 6379

    security_groups = [data.aws_security_group.teletracker-qa-external-sg.id, aws_security_group.all_crawlers.id]
  }

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    cidr_blocks = [
    "0.0.0.0/0"]
    ipv6_cidr_blocks = [
    "::/0"]
  }
}

resource "aws_network_interface" "crawl_cache" {
  subnet_id       = data.aws_subnet.us_west_2b.id
  security_groups = [aws_security_group.crawler_cache.id, aws_security_group.ssh_access.id]

  depends_on = [aws_security_group.ssh_access]
}

module "crawl_cache_one_off" {
  source               = "../modules/one-off-container-instance"
  snapshot_id          = "snap-0f6cf4b4deae5f20a"
  iam_instance_profile = data.aws_iam_instance_profile.ecs-instance-profile.arn
  user_data            = data.template_file.crawl_cache_user_data.rendered
  network_interface_id = aws_network_interface.crawl_cache.id
  security_groups      = [aws_security_group.crawler_cache.id]

  tags = {
    "purpose" : "redis"
  }
}

resource "aws_ecs_task_definition" "crawl_cache" {
  family = "crawl-cache"
  container_definitions = jsonencode([
    {
      name : "crawl-cache",
      image : "redis:6.0.6",
      //      cpu : var.cpu,
      //      memory : var.memory,
      essential : true,
      //      logConfiguration : {
      //        logDriver : "awslogs",
      //        options : {
      //          "awslogs-group" : "${var.spider_name}_crawler",
      //          "awslogs-region" : "us-west-2",
      //          "awslogs-stream-prefix" : "${var.spider_name}_crawler",
      //          "awslogs-create-group" : "true"
      //        }
      //      },
      stopTimeout : 120,
      portMappings : [{
        containerPort : 6379
        hostPort : 6379,
        protocol : "tcp"
      }]
    }
  ])
  execution_role_arn = data.aws_iam_role.ecs-task-execution-role.arn

  cpu          = 1024
  memory       = 768
  network_mode = "bridge"
}

resource "aws_ecs_service" "crawl_cache" {
  name            = "crawl_cache"
  cluster         = aws_ecs_cluster.teletracker-qa.id
  task_definition = aws_ecs_task_definition.crawl_cache.arn
  desired_count   = 1

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  ordered_placement_strategy {
    field = "attribute:ecs.availability-zone"
    type  = "spread"
  }

  ordered_placement_strategy {
    field = "instanceId"
    type  = "spread"
  }

  lifecycle {
    ignore_changes = [desired_count]
  }

  placement_constraints {
    expression = "attribute:purpose == redis"
    type       = "memberOf"
  }
}

resource "aws_route53_record" "crawl_store_dns_record" {
  name    = "crawl_store.cache.internal.qa.teletracker.tv"
  type    = "A"
  zone_id = data.aws_route53_zone.teletracker-tv-zone.zone_id
  ttl     = 60

  records = aws_network_interface.crawl_cache.private_ips
}