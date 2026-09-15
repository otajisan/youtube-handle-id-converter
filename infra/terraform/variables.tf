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
