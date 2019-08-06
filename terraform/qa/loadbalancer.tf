resource "google_compute_global_forwarding_rule" "api_frontend" {
  name        = "teletracker-api-frontend-qa"
  target      = "${google_compute_target_https_proxy.api_frontend.self_link}"
  port_range  = "443-443"
  ip_protocol = "TCP"
  ip_version  = "IPV4"
}

resource "google_compute_target_https_proxy" "api_frontend" {
  name             = "teletracker-qa-target-proxy"
  url_map          = "${google_compute_url_map.api_frontend.self_link}"
  ssl_certificates = ["${data.google_compute_ssl_certificate.api_cert.self_link}"]
  quic_override    = "NONE"
}

resource "google_compute_url_map" "api_frontend" {
  name            = "teletracker-qa"
  default_service = "${google_compute_backend_service.teletracker_backend_qa.self_link}"
}

resource "google_compute_backend_service" "teletracker_backend_qa" {
  name             = "teletracker-backend-qa"
  project          = "teletracker"
  session_affinity = "NONE"

  connection_draining_timeout_sec = 300
  enable_cdn                      = false

  health_checks = ["${google_compute_health_check.simple_alive_health_check.self_link}"]

  port_name = "api"
  protocol  = "HTTP"

  backend {
    balancing_mode  = "UTILIZATION"
    capacity_scaler = 1
    group           = "${google_compute_region_instance_group_manager.teletracker_backend_instance_group_manager.instance_group}"
  }
}

resource "google_compute_health_check" "simple_alive_health_check" {
  provider = "google-beta"

  name = "alive-check-1"

  timeout_sec         = 5
  check_interval_sec  = 10
  unhealthy_threshold = 3
  healthy_threshold   = 2

  http_health_check {
    request_path = "/health"
    port         = 3001
  }
}
