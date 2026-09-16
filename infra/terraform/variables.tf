# 環境は本番 1 つのため、値は default で持つ(秘密情報は含めない)
variable "project_id" {
  description = "GCP プロジェクト ID"
  type        = string
  default     = "yt-handle-id-converter"
}

variable "region" {
  description = "Cloud Run / Artifact Registry を配置するリージョン"
  type        = string
  default     = "asia-northeast1"
}

variable "github_repository" {
  description = "CD 用 SA を借用できる GitHub リポジトリ(owner/name)。main ブランチのトークンのみ許可する"
  type        = string
  default     = "otajisan/youtube-handle-id-converter"
}

variable "service_name" {
  description = "Cloud Run サービス名 / Artifact Registry リポジトリ名などに使う識別子"
  type        = string
  default     = "backend"
}

variable "initial_image" {
  description = "Cloud Run サービス作成時にだけ使うイメージ。null なら Artifact Registry の <service_name>:initial を使う。以後の image は CD が更新し Terraform は無視する(ignore_changes)"
  type        = string
  default     = null
}

variable "cors_allowed_origins" {
  description = "backend が許可する CORS オリジン(カンマ区切り)。GitHub Pages のオリジン"
  type        = string
  default     = "https://otajisan.github.io"
}

variable "max_inputs" {
  description = "1 リクエストで受け付ける変換件数の上限(Quota を見て調整する)"
  type        = number
  default     = 10
}

variable "maintenance_mode" {
  description = "true にすると /api/** が 503 を返す(Quota 枯渇時などの緊急停止)"
  type        = bool
  default     = false
}

variable "auth_enabled" {
  description = "true にすると /api/** に Basic 認証を要求する(資格情報は Secret Manager)"
  type        = bool
  default     = false
}

variable "rate_limit_per_minute" {
  description = "IP ごとの 1 分あたりリクエスト上限(インスタンス単位)。0 で無効"
  type        = number
  default     = 30
}

variable "alert_email" {
  description = "Quota アラートの通知先メールアドレス。public リポジトリに置かないため CI では TF_VAR_alert_email(Repository variable ALERT_EMAIL)で渡す"
  type        = string
  sensitive   = true # plan 出力(PR コメント)に載せない

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "alert_email はメールアドレス形式で指定する(ALERT_EMAIL の Repository variable が未設定の可能性)"
  }
}

variable "quota_daily_limit" {
  description = "YouTube Data API の 1 日の割り当て(unit)。Google の既定は 10,000"
  type        = number
  default     = 10000
}

variable "quota_alert_ratio" {
  description = "直近 24 時間の消費がこの割合を超えたらアラートする(0〜1)"
  type        = number
  default     = 0.8
}
