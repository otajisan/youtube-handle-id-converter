output "project_number" {
  description = "GCP プロジェクト番号"
  value       = data.google_project.this.number
}
