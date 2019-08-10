resource "google_sql_database_instance" "teletracker_db" {
  name             = "teletracker"
  database_version = "POSTGRES_11"
  region           = "us-east1"

  settings {
    tier                        = "db-f1-micro"
    activation_policy           = "ALWAYS"
    authorized_gae_applications = []
    availability_type           = "ZONAL"
    crash_safe_replication      = false
    disk_autoresize             = false
    disk_size                   = 10
    disk_type                   = "PD_SSD"
    ip_configuration {
      ipv4_enabled    = "true"
      private_network = "${data.google_compute_network.default_private_network.self_link}"
      require_ssl     = true

      authorized_networks {
        name  = "Christian Home"
        value = "67.164.191.249"
      }

    }

    location_preference {
      zone = "us-east1-c"
    }
  }
}
