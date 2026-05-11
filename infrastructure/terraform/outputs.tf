output "server_ip" {
  description = "Existing VM fixed IP"
  value       = var.existing_server_ip
}

output "server_name" {
  value = var.existing_server_name
}

output "postgres_volume_id" {
  description = "Cinder volume ID attached to VM"
  value       = openstack_blockstorage_volume_v3.postgres_data.id
}

output "ssh_command" {
  value = "ssh ubuntu@${var.existing_server_ip}"
}