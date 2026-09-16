# backend の Cloud Run サービス。
# - image は作成時のみ var.initial_image を使い、以後は CD(#9)が差し替える(ignore_changes)
# - アプリは 8180、Actuator は 8181(ingress 非公開)。probe は 8181 を直接指定する(#3 の決定)
# - 保護機構のフラグ(APP_MAINTENANCE_MODE / APP_AUTH_ENABLED)は Terraform 変数で切り替える
locals {
  initial_image = coalesce(
    var.initial_image,
    "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.backend.repository_id}/${var.service_name}:initial",
  )
}

resource "google_cloud_run_v2_service" "backend" {
  name     = var.service_name
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.runtime.email

    scaling {
      min_instance_count = 0 # 待機コストをゼロにする(コールドスタートは許容)
      max_instance_count = 2 # bot 等による暴走時の上限
    }

    containers {
      image = local.initial_image

      ports {
        container_port = 8180 # Cloud Run がこの値を PORT として注入する
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = true # リクエスト処理中のみ CPU 課金
        startup_cpu_boost = true # JVM 起動を速くする
      }

      env {
        name  = "MANAGEMENT_PORT"
        value = "8181"
      }
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "gcp"
      }
      env {
        name  = "APP_CORS_ALLOWED_ORIGINS"
        value = var.cors_allowed_origins
      }
      env {
        name  = "APP_MAX_INPUTS"
        value = tostring(var.max_inputs)
      }
      env {
        name  = "APP_MAINTENANCE_MODE"
        value = tostring(var.maintenance_mode)
      }
      env {
        name  = "APP_AUTH_ENABLED"
        value = tostring(var.auth_enabled)
      }
      env {
        name  = "APP_RATE_LIMIT_PER_MINUTE"
        value = tostring(var.rate_limit_per_minute)
      }

      dynamic "env" {
        for_each = google_secret_manager_secret.this
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value.secret_id
              version = "latest"
            }
          }
        }
      }

      # Actuator は management ポート(ingress 非公開)。probe はコンテナ内から到達できる
      startup_probe {
        http_get {
          path = "/actuator/health/readiness"
          port = 8181
        }
        initial_delay_seconds = 5
        period_seconds        = 5
        timeout_seconds       = 3
        failure_threshold     = 12 # 最大 60 秒待つ
      }
      liveness_probe {
        http_get {
          path = "/actuator/health/liveness"
          port = 8181
        }
        period_seconds    = 30
        timeout_seconds   = 3
        failure_threshold = 3
      }
    }
  }

  lifecycle {
    ignore_changes = [
      template[0].containers[0].image, # CD が更新する
      client,                          # gcloud / deploy-cloudrun が付与する
      client_version,
    ]
  }

  depends_on = [
    google_project_service.this["run.googleapis.com"],
    google_secret_manager_secret_iam_member.runtime_accessor,
  ]
}

# 誰でも呼び出せる公開サービス。保護は Basic 認証 / メンテナンスモード(アプリ側)で行う
resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  name     = google_cloud_run_v2_service.backend.name
  location = google_cloud_run_v2_service.backend.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# CD 用 SA はこのサービスのリビジョン更新だけができる(サービス単位、プロジェクト全体ではない)
resource "google_cloud_run_v2_service_iam_member" "deploy_developer" {
  name     = google_cloud_run_v2_service.backend.name
  location = google_cloud_run_v2_service.backend.location
  role     = "roles/run.developer"
  member   = google_service_account.deploy.member
}
