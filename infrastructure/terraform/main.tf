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

locals {
  use_existing_server = trimspace(var.existing_server_id) != ""
}

data "openstack_compute_instance_v2" "existing_server" {
  count = local.use_existing_server ? 1 : 0
  id    = var.existing_server_id
}

resource "openstack_compute_instance_v2" "app_server" {
  count       = local.use_existing_server ? 0 : 1
  name        = var.existing_server_name
  image_name  = var.image_name
  flavor_name = var.flavor_name
  key_pair    = var.key_pair

  security_groups = [var.security_group]

  network {
    uuid = var.network_id
  }
}

resource "openstack_blockstorage_volume_v3" "postgres_data" {
  name = var.volume_name
  size = var.volume_size_gb
}

resource "openstack_compute_volume_attach_v2" "postgres_attach" {
  instance_id = local.use_existing_server ? data.openstack_compute_instance_v2.existing_server[0].id : openstack_compute_instance_v2.app_server[0].id
  volume_id   = openstack_blockstorage_volume_v3.postgres_data.id
}