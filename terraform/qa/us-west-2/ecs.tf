resource "aws_ecs_cluster" "teletracker-qa" {
  name = "teletracker-qa"
}

resource "aws_ecs_cluster" "teletracker_crawlers" {
  name = "teletracker-crawlers-qa"
}