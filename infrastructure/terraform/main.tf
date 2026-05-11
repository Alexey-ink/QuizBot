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

resource "openstack_blockstorage_volume_v3" "postgres_data" {
  name = var.volume_name
  size = var.volume_size_gb
}

resource "openstack_compute_volume_attach_v2" "postgres_attach" {
  instance_id = var.existing_server_id
  volume_id   = openstack_blockstorage_volume_v3.postgres_data.id
}