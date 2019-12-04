data "aws_security_group" "teletracker-qa-external-sg" {
  id = "sg-46677c2a"
}

data "aws_vpc" "teletracker-qa-vpc" {
  id = "vpc-95a65cfd"
}

data "aws_subnet_ids" "teletracker-qa-vpc-subnets" {
  vpc_id = data.aws_vpc.teletracker-qa-vpc.id
}

# resource "aws_lb" "teletracker-qa-server" {
#   name               = "teletracker-qa"
#   internal           = false
#   load_balancer_type = "application"
#   security_groups    = [data.aws_security_group.teletracker-qa-external-sg.id]
#   subnets            = data.aws_subnet_ids.teletracker-qa-vpc-subnets.ids

#   enable_deletion_protection = true
# }

resource "aws_lb_target_group" "teletracker-qa-server" {
  name     = "teletracker-qa-http"
  port     = 80
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.teletracker-qa-vpc.id

  health_check {
    interval            = 30
    timeout             = 5
    healthy_threshold   = 5
    unhealthy_threshold = 5
    path                = "/health"
    protocol            = "HTTP"
  }
}
