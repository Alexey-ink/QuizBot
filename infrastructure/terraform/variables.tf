variable "folder_id" {
  type        = string
  description = "Yandex Cloud Folder ID"
  # IMPORTANT: do not commit real cloud IDs in git. Pass via TF_VAR_folder_id / -var or tfvars locally.
  default     = ""
}

variable "subnet_id" {
  type        = string
  description = "Existing Subnet ID"
  default     = ""
}

variable "ssh_public_key" {
  type        = string
  description = "Public SSH key for ubuntu user"
  default = ""
}

variable "image_family" {
  type    = string
  default = "ubuntu-2204-lts"
}

variable "security_group_id" {
  type        = string
  description = "security group id"
  default     = ""
}

variable "yc_token" {
  type      = string
  sensitive = true
}

variable "postgres_password" {
  type        = string
  description = "Password for PostgreSQL user"
  sensitive   = true
  # IMPORTANT: never commit real passwords. Provide via secrets/CI.
  default     = ""
}