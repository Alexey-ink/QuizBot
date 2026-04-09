terraform {
  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = "~> 0.130"
    }
  }
  required_version = ">= 1.0"
}

provider "yandex" {
  folder_id = var.folder_id
  token     = var.yc_token
}

data "yandex_vpc_subnet" "main" {
  subnet_id = var.subnet_id
}

data "yandex_compute_image" "ubuntu" {
  family = var.image_family
}

# диск для PostgreSQL
resource "yandex_compute_disk" "postgres_data" {
  name     = "quizbot-postgres-data"
  type     = "network-hdd"    
  zone     = data.yandex_vpc_subnet.main.zone
  size     = 10              
  
}

# Виртуальная машина с подключённым диском
resource "yandex_compute_instance" "quizbot_server" {
  name        = "quizbot"
  platform_id = "standard-v3"
  zone        = data.yandex_vpc_subnet.main.zone

  resources {
    cores         = 2
    memory        = 2
    core_fraction = 20 
  }

  boot_disk {
    initialize_params {
      image_id = data.yandex_compute_image.ubuntu.id
      size     = 10
      type     = "network-hdd"
    }
  }

  # подключаем диск postgres
  secondary_disk {
    disk_id = yandex_compute_disk.postgres_data.id
    mode    = "READ_WRITE"
  }

  network_interface {
    subnet_id          = var.subnet_id
    nat                = true
    security_group_ids = [var.security_group_id]
  }

  metadata = {
    ssh-keys = "ubuntu:${var.ssh_public_key}"
  }
  
  # позволяет обновлять диск без уничтожения ВМ
  allow_stopping_for_update = true

}

resource "yandex_vpc_security_group_rule" "allow_quizbot_http" {
  security_group_binding = var.security_group_id
  direction              = "ingress"
  description            = "Allow HTTP access to QuizBot application"
  protocol               = "TCP"
  port                   = 8080
  v4_cidr_blocks         = ["0.0.0.0/0"]  # Разрешить всем, или укажите конкретные IP
}