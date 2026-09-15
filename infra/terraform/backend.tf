# state は GCS(バージョニング有効)。バケットは infra/bootstrap.sh が作成する。
# 認証は CI では WIF、ローカルでは gcloud のアクセストークン(docs/infra.md 参照)
terraform {
  backend "gcs" {
    bucket = "yt-handle-id-converter-tfstate"
    prefix = "terraform/state"
  }
}
