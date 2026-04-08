output "server_public_ip" {
  description = "Public IP address"
  value       = yandex_compute_instance.quizbot_server.network_interface[0].nat_ip_address
}

output "server_name" {
  value = yandex_compute_instance.quizbot_server.name
}

output "postgres_disk_id" {
  description = "Disk ID for PostgreSQL data (for manual snapshot/restore)"
  value       = yandex_compute_disk.postgres_data.id
  sensitive   = false
}

output "ssh_command" {
  value = "ssh -i ~/.ssh/id_ed25519 ubuntu@${yandex_compute_instance.quizbot_server.network_interface[0].nat_ip_address}"
}