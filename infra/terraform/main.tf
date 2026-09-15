# bootstrap 済みリソースの参照。実体は infra/bootstrap.sh が作成し、Terraform では管理しない
data "google_project" "this" {
  project_id = var.project_id
}
