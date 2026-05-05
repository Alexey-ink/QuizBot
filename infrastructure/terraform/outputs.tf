output "server_ip" {
  description = "Instance fixed IP"
  value = coalesce(
    openstack_compute_instance_v2.quizbot_server.access_ip_v4,
    openstack_compute_instance_v2.quizbot_server.network[0].fixed_ip_v4
  )
}

output "server_name" {
  value = openstack_compute_instance_v2.quizbot_server.name
}

output "postgres_volume_id" {
  description = "Cinder volume ID attached to VM"
  value       = openstack_blockstorage_volume_v3.postgres_data.id
}

output "ssh_command" {
  value = "ssh ubuntu@${coalesce(openstack_compute_instance_v2.quizbot_server.access_ip_v4, openstack_compute_instance_v2.quizbot_server.network[0].fixed_ip_v4)}"
}