resource "aws_acm_certificate" "qa-teletracker-tv-cert-us-east-1" {
  provider = "aws.us-east-1"

  domain_name = "qa.teletracker.tv"

  subject_alternative_names = ["*.qa.teletracker.tv", "*.internal.qa.teletracker.tv"]

  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

data "aws_route53_zone" "teletracker-tv" {
  name         = "teletracker.tv."
  private_zone = false
}

resource "aws_route53_record" "qa-teletracker-tv-cert-validation" {
  name    = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[0].resource_record_name
  type    = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[0].resource_record_type
  zone_id = data.aws_route53_zone.teletracker-tv.id
  records = [
  aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[0].resource_record_value]
  ttl = 60
}

resource "aws_route53_record" "qa-teletracker-tv-cert-validation-alt1" {
  name    = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[1].resource_record_name
  type    = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[1].resource_record_type
  zone_id = data.aws_route53_zone.teletracker-tv.id
  records = [
  aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[1].resource_record_value]
  ttl = 60
}

resource "aws_route53_record" "qa-teletracker-tv-cert-validation-alt2" {
  name    = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[2].resource_record_name
  type    = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[2].resource_record_type
  zone_id = data.aws_route53_zone.teletracker-tv.id
  records = [
  aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.domain_validation_options[2].resource_record_value]
  ttl = 60
}

resource "aws_acm_certificate_validation" "qa-teletracker-tv-cert-validation" {
  provider = "aws.us-east-1"

  certificate_arn = aws_acm_certificate.qa-teletracker-tv-cert-us-east-1.arn
  validation_record_fqdns = [
    aws_route53_record.qa-teletracker-tv-cert-validation.fqdn,
    aws_route53_record.qa-teletracker-tv-cert-validation-alt1.fqdn,
  aws_route53_record.qa-teletracker-tv-cert-validation-alt2.fqdn]
}