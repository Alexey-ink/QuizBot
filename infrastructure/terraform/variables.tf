variable "auth_url" {
  type        = string
  description = "OpenStack Keystone URL, e.g. https://cloud.crplab.ru:5000/v3"
}

variable "username" {
  type        = string
  description = "OpenStack username"
}

variable "password" {
  type        = string
  description = "OpenStack password"
  sensitive   = true
}

variable "project_name" {
  type        = string
  description = "OpenStack project/tenant name"
}

variable "user_domain_name" {
  type        = string
  description = "OpenStack user domain"
  default     = "Default"
}

variable "project_domain_name" {
  type        = string
  description = "OpenStack project domain"
  default     = "Default"
}

variable "region" {
  type        = string
  description = "OpenStack region name"
  default     = "RegionOne"
}

variable "network_id" {
  type        = string
  description = "OpenStack network UUID where VM NIC will be created"
}

variable "image_name" {
  type        = string
  description = "OpenStack image name"
  default     = "ubuntu-24.04"
}

variable "flavor_name" {
  type        = string
  description = "OpenStack flavor name"
  default     = "m1.small"
}

variable "server_name" {
  type        = string
  description = "VM name"
  default     = "emeshkin-bot-vm"
}

variable "volume_name" {
  type        = string
  description = "Cinder volume name for PostgreSQL data"
  default     = "emeshkin-postgres-vol"
}

variable "volume_size_gb" {
  type        = number
  description = "PostgreSQL data volume size in GiB"
  default     = 10
}

variable "keypair_name" {
  type        = string
  description = "Name of the keypair created in OpenStack"
  default     = "emeshkin-key"
}

variable "ssh_public_key" {
  type        = string
  description = "Public SSH key contents"
}

variable "create_security_group" {
  type        = bool
  description = "If true, create SG with 22/8080 ingress. If false, use existing SG by name."
  default     = true
}

variable "security_group_name" {
  type        = string
  description = "Existing SG name when create_security_group=false OR name for new SG"
  default     = "emeshkin-sg"
}