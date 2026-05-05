terraform {
  required_providers {
    openstack = {
      source  = "terraform-provider-openstack/openstack"
      version = "~> 1.54"
    }
  }
  required_version = ">= 1.0"
}

provider "openstack" {
  auth_url            = var.auth_url
  user_name           = var.username
  password            = var.password
  tenant_name         = var.project_name
  user_domain_name    = var.user_domain_name
  project_domain_name = var.project_domain_name
  region              = var.region
}

resource "openstack_networking_secgroup_v2" "quizbot_sg" {
  count       = var.create_security_group ? 1 : 0
  name        = var.security_group_name
  description = "Security group for QuizBot VM"
}

resource "openstack_networking_secgroup_rule_v2" "ssh_ingress" {
  count             = var.create_security_group ? 1 : 0
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 22
  port_range_max    = 22
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.quizbot_sg[0].id
}

resource "openstack_networking_secgroup_rule_v2" "app_ingress" {
  count             = var.create_security_group ? 1 : 0
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 8080
  port_range_max    = 8080
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.quizbot_sg[0].id
}

resource "openstack_blockstorage_volume_v3" "postgres_data" {
  name = var.volume_name
  size = var.volume_size_gb
}

resource "openstack_compute_instance_v2" "quizbot_server" {
  name        = var.server_name
  image_name  = var.image_name
  flavor_name = var.flavor_name
  key_pair    = var.keypair_name

  network {
    port = openstack_networking_port_v2.quizbot_port.id
  }
}

resource "openstack_networking_port_v2" "quizbot_port" {
  name       = "${var.server_name}-port"
  network_id = var.network_id

  security_group_ids = var.create_security_group ? [openstack_networking_secgroup_v2.quizbot_sg[0].id] : []
}

resource "openstack_compute_volume_attach_v2" "postgres_attach" {
  instance_id = openstack_compute_instance_v2.quizbot_server.id
  volume_id   = openstack_blockstorage_volume_v3.postgres_data.id
}