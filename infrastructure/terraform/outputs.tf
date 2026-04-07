output "server_public_ip" {
  description = "Public IP address"
  value       = yandex_compute_instance.quizbot_server.network_interface[0].nat_ip_address
}

output "server_name" {
  value = yandex_compute_instance.quizbot_server.name
}

output "ssh_command" {
  value = "ssh -i ~/.ssh/id_ed25519 ubuntu@${yandex_compute_instance.quizbot_server.network_interface[0].nat_ip_address}"
}