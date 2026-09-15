# backend のコンテナイメージ置き場。CD(#9)が SHA タグで push する
resource "google_artifact_registry_repository" "backend" {
  location      = var.region
  repository_id = var.service_name
  format        = "DOCKER"
  description   = "backend container images"

  # docker_config { immutable_tags = false } は API が返さず永続差分になるため書かない(デフォルトで mutable)

  # 直近 10 バージョンは常に保持し、それ以外で 30 日より古いものを削除する
  cleanup_policy_dry_run = false
  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10
    }
  }
  cleanup_policies {
    id     = "delete-old"
    action = "DELETE"
    condition {
      older_than = "2592000s" # 30 days
    }
  }

  depends_on = [google_project_service.this["artifactregistry.googleapis.com"]]
}
