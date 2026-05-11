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

variable "existing_server_id" {
  type        = string
  description = "Existing VM UUID to reuse (no new VM will be created)"
}

variable "existing_server_name" {
  type        = string
  description = "Existing VM name"
  default     = "emeshkin-bot-vm"
}

variable "existing_server_ip" {
  type        = string
  description = "Existing VM private IP"
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
