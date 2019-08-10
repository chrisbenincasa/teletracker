data "google_compute_global_address" "private_ip_address" {
  provider = "google-beta"
  name     = "google-managed-services-default"
}

data "google_compute_network" "default_private_network" {
  name = "default"
}

data "google_compute_ssl_certificate" "api_cert" {
  name = "teletracker-qa-api-cert-2"
}
