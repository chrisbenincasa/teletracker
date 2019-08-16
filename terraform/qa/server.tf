module "gce-container" {
  source = "github.com/terraform-google-modules/terraform-google-container-vm?ref=v1.0.0"

  container = {
    name  = "teletracker-api-${var.env}"
    image = "${var.image}"
    env = [
      {
        name  = "ENV"
        value = "${var.env}"
        }, {
        name  = "DB_HOST"
        value = "127.0.0.1"
        }, {
        name  = "DB_PASSWORD"
        value = "berglas://teletracker-secrets/db-password-${var.env}"
        }, {
        name  = "JWT_SECRET"
        value = "berglas://teletracker-secrets/jwt-secret-key-${var.env}"
        }, {
        name  = "TMDB_API_KEY"
        value = "berglas://teletracker-secrets/tmdb-api-key-${var.env}"
        }, {
        name  = "SQL_INSTANCE"
        value = "teletracker:us-east1:teletracker"
        }, {
        name  = "ADMINISTRATOR_KEY"
        value = "berglas://teletracker-secrets/administrator-key-${var.env}"
      }, {
        name "APPLICATION_NAME"
        value = "teletracker-server"
      }
    ]
  }
  restart_policy = "Always"
}

resource "google_compute_instance_template" "teletracker_backend_instance_template" {
  project     = "${var.project_id}"
  name_prefix = "teleteacker-api-${var.env}-"

  machine_type = "g1-small"

  region = "${var.region}"

  network_interface {
    network = "default"
    access_config {
      network_tier = "PREMIUM"
    }
  }

  disk {
    auto_delete  = true
    boot         = true
    source_image = "${module.gce-container.source_image}"
    type         = "PERSISTENT"
    disk_type    = "pd-standard"
    disk_size_gb = 10
  }

  service_account {
    email  = "${var.compute_service_account}"
    scopes = ["https://www.googleapis.com/auth/cloud-platform"] # All access
  }

  metadata = merge(var.additional_metadata, map("gce-container-declaration", module.gce-container.metadata_value, "google-logging-enabled", "true"))

  labels = {
    "container-vm" = module.gce-container.vm_container_label
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_region_instance_group_manager" "teletracker_backend_instance_group_manager" {
  provider = "google-beta"
  name     = "teletracker-api-${var.env}"

  base_instance_name = "teletracker-api-${var.env}"
  region             = "us-east1"

  named_port {
    name = "api"
    port = 3001
  }

  auto_healing_policies {
    health_check      = "${google_compute_health_check.simple_alive_health_check.self_link}"
    initial_delay_sec = 300
  }

  target_size = 1

  version {
    name              = "teletracker-api-${var.env}-main"
    instance_template = "${google_compute_instance_template.teletracker_backend_instance_template.self_link}"
    # target_size {
    # fixed = 1
    # }
  }

  update_policy {
    max_surge_fixed = 3
    minimal_action  = "REPLACE"
    type            = "PROACTIVE"
  }
}
