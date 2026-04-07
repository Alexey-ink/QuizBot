variable "folder_id" {
  type        = string
  description = "Yandex Cloud Folder ID"
  default     = "b1gkeihpvcehagj9bjlf"
}

variable "subnet_id" {
  type        = string
  description = "Existing Subnet ID"
  default     = "fl80id702e4irnblcd63"
}

variable "ssh_public_key" {
  type        = string
  description = "Public SSH key for ubuntu user"
}

variable "image_family" {
  type    = string
  default = "ubuntu-2204-lts"
}

variable "security_group_id" {
  type        = string
  description = "security group id"
  default     = "enpkd9np0qbhc064o9mu"
}

variable "yc_token" {
  type      = string
  sensitive = true
}

variable "postgres_password" {
  type        = string
  description = "Password for PostgreSQL user"
  sensitive   = true
}