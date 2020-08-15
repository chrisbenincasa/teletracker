data "aws_security_group" "teletracker-qa-external-sg" {
  id = "sg-0134d00655bb69093"
}

data "aws_vpc" "teletracker-qa-vpc" {
  id = "vpc-09a64ee30f2e3e82e"
}

data "aws_subnet_ids" "teletracker-qa-vpc-subnets" {
  vpc_id = data.aws_vpc.teletracker-qa-vpc.id
}

data "aws_acm_certificate" "qa_teletracker_cert" {
  domain = "qa.teletracker.tv"
}

resource "aws_lb" "teletracker_qa" {
  name                       = "teletracker-qa"
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [data.aws_security_group.teletracker-qa-external-sg.id]
  subnets                    = data.aws_subnet_ids.teletracker-qa-vpc-subnets.ids
  enable_deletion_protection = true
}

resource "aws_lb_listener" "https_forward" {
  load_balancer_arn = aws_lb.teletracker_qa.arn
  certificate_arn   = data.aws_acm_certificate.qa_teletracker_cert.arn
  protocol          = "HTTPS"
  port              = 443

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.teletracker_qa_frontend.arn
  }
}

resource "aws_lb_listener_rule" "backend_routing" {
  listener_arn = aws_lb_listener.https_forward.arn

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.teletracker_qa_server.arn
  }

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}

resource "aws_lb_listener" "https_redirect" {
  load_balancer_arn = aws_lb.teletracker_qa.arn
  protocol          = "HTTP"
  port              = 80

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_target_group" "teletracker_qa_server" {
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

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [aws_lb.teletracker_qa]
}