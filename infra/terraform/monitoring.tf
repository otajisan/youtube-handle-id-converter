# YouTube Data API の Quota 消費を可視化し、枯渇の予兆と枯渇そのものをメールで通知する。
#
# - 消費: serviceruntime.googleapis.com/quota/rate/net_usage(DELTA、unit)を合計する
# - 上限: serviceruntime.googleapis.com/quota/limit(limit_name=defaultPerDayPerProject、既定 10,000)
# - 枯渇: serviceruntime.googleapis.com/quota/exceeded(BOOL)
# Google の日次リセットは太平洋時間 0 時だが、Monitoring は「直近 24 時間」の窓でしか集計できないため近似になる。
locals {
  quota_filter = join(" AND ", [
    "resource.type = \"consumer_quota\"",
    "resource.labels.service = \"youtube.googleapis.com\"",
  ])
  quota_alert_threshold = floor(var.quota_daily_limit * var.quota_alert_ratio)
}

# ---- 通知先(メール)-------------------------------------------------------------
resource "google_monitoring_notification_channel" "email" {
  display_name = "運用者メール"
  type         = "email"
  labels = {
    email_address = var.alert_email
  }

  depends_on = [google_project_service.this["monitoring.googleapis.com"]]
}

# ---- アラート 1: 直近 24 時間の消費が上限の一定割合を超えた --------------------------
resource "google_monitoring_alert_policy" "quota_usage_high" {
  display_name = "YouTube Data API: Quota 消費が ${var.quota_alert_ratio * 100}% を超過"
  combiner     = "OR"
  severity     = "WARNING"

  conditions {
    display_name = "直近 24 時間の消費 unit > ${local.quota_alert_threshold}"

    condition_threshold {
      filter          = "metric.type = \"serviceruntime.googleapis.com/quota/rate/net_usage\" AND ${local.quota_filter}"
      comparison      = "COMPARISON_GT"
      threshold_value = local.quota_alert_threshold
      duration        = "0s"

      aggregations {
        alignment_period     = "86400s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.email.id]

  documentation {
    mime_type = "text/markdown"
    content   = <<-EOT
      YouTube Data API の直近 24 時間の Quota 消費が ${local.quota_alert_threshold} unit(上限 ${var.quota_daily_limit} の ${var.quota_alert_ratio * 100}%)を超えました。
      bot 等による急増でないか Cloud Logging で `/api/v1/convert` のリクエスト元を確認し、必要なら `docs/operations.md` の手順でメンテナンスモードや Basic 認証を有効にしてください。
    EOT
  }

  alert_strategy {
    # 同じ状態が続く間の再通知は 1 日 1 回まで(notification_rate_limit はログベース専用なので使わない)
    notification_channel_strategy {
      notification_channel_names = [google_monitoring_notification_channel.email.id]
      renotify_interval          = "86400s"
    }
    auto_close = "172800s"
  }
}

# ---- アラート 2: 割り当てを使い切った --------------------------------------------------
resource "google_monitoring_alert_policy" "quota_exceeded" {
  display_name = "YouTube Data API: Quota 枯渇"
  combiner     = "OR"
  severity     = "ERROR"

  conditions {
    display_name = "quota/exceeded(defaultPerDayPerProject)が true"

    condition_threshold {
      filter          = "metric.type = \"serviceruntime.googleapis.com/quota/exceeded\" AND ${local.quota_filter} AND metric.labels.limit_name = \"defaultPerDayPerProject\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "0s"

      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_COUNT_TRUE"
      }

      trigger {
        count = 1
      }
    }
  }

  notification_channels = [google_monitoring_notification_channel.email.id]

  documentation {
    mime_type = "text/markdown"
    content   = <<-EOT
      YouTube Data API の 1 日の割り当てを使い切りました。太平洋時間 0 時にリセットされるまで変換 API は 429 を返します。
      利用者への影響を抑えるにはメンテナンスモードを有効にしてください(`docs/operations.md`)。
    EOT
  }

  alert_strategy {
    # 同じ状態が続く間の再通知は 1 日 1 回まで(notification_rate_limit はログベース専用なので使わない)
    notification_channel_strategy {
      notification_channel_names = [google_monitoring_notification_channel.email.id]
      renotify_interval          = "86400s"
    }
    auto_close = "172800s"
  }
}

# ---- ダッシュボード ---------------------------------------------------------------
resource "google_monitoring_dashboard" "quota" {
  dashboard_json = jsonencode({
    displayName = "YouTube Data API Quota"
    mosaicLayout = {
      columns = 12
      tiles = [
        {
          xPos = 0, yPos = 0, width = 4, height = 4
          widget = {
            title = "直近 24 時間の消費 unit"
            scorecard = {
              timeSeriesQuery = {
                timeSeriesFilter = {
                  filter = "metric.type = \"serviceruntime.googleapis.com/quota/rate/net_usage\" AND ${local.quota_filter}"
                  aggregation = {
                    alignmentPeriod    = "86400s"
                    perSeriesAligner   = "ALIGN_SUM"
                    crossSeriesReducer = "REDUCE_SUM"
                  }
                }
              }
              thresholds = [
                { value = local.quota_alert_threshold, color = "YELLOW", direction = "ABOVE" },
                { value = var.quota_daily_limit, color = "RED", direction = "ABOVE" },
              ]
            }
          }
        },
        {
          xPos = 4, yPos = 0, width = 8, height = 4
          widget = {
            title = "1 時間ごとの消費 unit(method 別)"
            xyChart = {
              dataSets = [{
                plotType = "STACKED_BAR"
                timeSeriesQuery = {
                  timeSeriesFilter = {
                    filter = "metric.type = \"serviceruntime.googleapis.com/quota/rate/net_usage\" AND ${local.quota_filter}"
                    aggregation = {
                      alignmentPeriod    = "3600s"
                      perSeriesAligner   = "ALIGN_SUM"
                      crossSeriesReducer = "REDUCE_SUM"
                      groupByFields      = ["metric.label.method"]
                    }
                  }
                }
              }]
              thresholds = [{ value = local.quota_alert_threshold, color = "YELLOW", direction = "ABOVE", label = "アラート閾値(24h)" }]
            }
          }
        },
        {
          xPos = 0, yPos = 4, width = 6, height = 4
          widget = {
            title = "1 日の割り当て上限(defaultPerDayPerProject)"
            xyChart = {
              dataSets = [{
                plotType = "LINE"
                timeSeriesQuery = {
                  timeSeriesFilter = {
                    filter = "metric.type = \"serviceruntime.googleapis.com/quota/limit\" AND ${local.quota_filter} AND metric.labels.limit_name = \"defaultPerDayPerProject\""
                    aggregation = {
                      alignmentPeriod  = "3600s"
                      perSeriesAligner = "ALIGN_MEAN"
                    }
                  }
                }
              }]
            }
          }
        },
        {
          xPos = 6, yPos = 4, width = 6, height = 4
          widget = {
            title = "Quota 枯渇(exceeded)"
            xyChart = {
              dataSets = [{
                plotType = "STACKED_BAR"
                timeSeriesQuery = {
                  timeSeriesFilter = {
                    filter = "metric.type = \"serviceruntime.googleapis.com/quota/exceeded\" AND ${local.quota_filter} AND metric.labels.limit_name = \"defaultPerDayPerProject\""
                    aggregation = {
                      alignmentPeriod  = "300s"
                      perSeriesAligner = "ALIGN_COUNT_TRUE"
                    }
                  }
                }
              }]
            }
          }
        },
      ]
    }
  })

  depends_on = [google_project_service.this["monitoring.googleapis.com"]]
}
