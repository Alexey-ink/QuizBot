output "server_ip" {
  description = "VM fixed IP (existing or newly created)"
  value = trimspace(var.existing_server_id) != ""
    ? (trimspace(var.existing_server_ip) != "" ? var.existing_server_ip : data.openstack_compute_instance_v2.existing_server[0].access_ip_v4)
    : openstack_compute_instance_v2.app_server[0].access_ip_v4
}

output "server_name" {
  value = trimspace(var.existing_server_id) != "" ? data.openstack_compute_instance_v2.existing_server[0].name : openstack_compute_instance_v2.app_server[0].name
}

output "postgres_volume_id" {
  description = "Cinder volume ID attached to VM"
  value       = openstack_blockstorage_volume_v3.postgres_data.id
}

output "ssh_command" {
  value = trimspace(var.existing_server_id) != ""
    ? "ssh ubuntu@${trimspace(var.existing_server_ip) != "" ? var.existing_server_ip : data.openstack_compute_instance_v2.existing_server[0].access_ip_v4}"
    : "ssh ubuntu@${openstack_compute_instance_v2.app_server[0].access_ip_v4}"
}