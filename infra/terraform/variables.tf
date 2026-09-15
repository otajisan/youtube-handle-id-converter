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
