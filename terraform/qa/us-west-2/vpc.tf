resource "aws_security_group" "ssh_access" {
  name        = "SSH Access"
  description = "Provides SSH access for anybody"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  ingress {
    from_port   = 22
    protocol    = "tcp"
    to_port     = 22
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = [
    "::/0"]
  }
}

resource "aws_security_group" "load_balancer_access" {
  name = "Load Balancer Access"
  description = "Provides HTTP/S support for all ports coming from the load balancer"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  ingress {
    from_port = 0
    protocol = "-1"
    to_port = 0
    security_groups = aws_lb.teletracker_qa.security_groups
  }

  egress {
    from_port = 0
    protocol = "-1"
    to_port = 0
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs-instance-sg" {
  name        = "ECS-Public-Services"
  description = "Allows incoming traffic for HTTP based services"

  vpc_id = data.aws_vpc.teletracker-qa-vpc.id

  ingress {
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    from_port        = 22
    to_port          = 22
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}