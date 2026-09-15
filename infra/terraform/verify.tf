# [検証用] trivy config が HIGH 以上として検出する設定ミス
resource "google_compute_firewall" "verify_open_ssh" {
  name    = "verify-open-ssh"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
}
