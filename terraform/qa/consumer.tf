module "consumer-container-image" {
  source = "github.com/terraform-google-modules/terraform-google-container-vm?ref=v1.0.0"

  container = {
    name  = "teletracker-consumer-${var.env}"
    image = "${var.consumer_image}"
    args  = ["-topic=teletracker-task-queue"]
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
        name  = "APPLICATION_NAME"
        value = "teletracker-consumer"
      }
    ]
  }
  restart_policy = "Always"
}

resource "google_compute_instance_template" "teletracker_consumer_instance_template" {
  project     = "${var.project_id}"
  name_prefix = "teleteacker-consumer-${var.env}-"

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
    source_image = "${module.consumer-container-image.source_image}"
    type         = "PERSISTENT"
    disk_type    = "pd-standard"
    disk_size_gb = 10
  }

  service_account {
    email  = "${var.compute_service_account}"
    scopes = ["https://www.googleapis.com/auth/cloud-platform"] # All access
  }

  metadata = merge(var.additional_metadata, map("gce-container-declaration", module.consumer-container-image.metadata_value, "google-logging-enabled", "true"))

  labels = {
    "container-vm" = module.consumer-container-image.vm_container_label
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_region_instance_group_manager" "teletracker_consumer_instance_group_manager" {
  provider = "google-beta"
  name     = "teletracker-consumer-${var.env}"

  base_instance_name = "teletracker-consumer-${var.env}"
  region             = "us-east1"

  target_size = 1

  version {
    name              = "teletracker-consumer-${var.env}-main"
    instance_template = "${google_compute_instance_template.teletracker_consumer_instance_template.self_link}"
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
