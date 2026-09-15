# infra

Terraform による GCP リソース定義と bootstrap スクリプト。詳細は [`docs/infra.md`](../docs/infra.md)。

| パス | 内容 |
|---|---|
| [`bootstrap.sh`](bootstrap.sh) | Terraform 管理に入る前の最小限の手動セットアップ(冪等) |
| [`terraform/`](terraform/) | root module(環境は本番 1 つ、GCS backend) |
