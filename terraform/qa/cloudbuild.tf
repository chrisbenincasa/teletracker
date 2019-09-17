resource "google_cloudbuild_trigger" "push_to_master_trigger" {
  description = "Push to master"

  trigger_template {
    branch_name = "master"
    repo_name   = "github_chrisbenincasa_teletracker"
  }

  filename = "cloudbuild.yaml"

  included_files = ["scala-server/**"]

  disabled = "true"
}
