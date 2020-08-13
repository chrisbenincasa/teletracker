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