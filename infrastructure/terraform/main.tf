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

# Данные о подсети и образе
data "yandex_vpc_subnet" "main" {
  subnet_id = var.subnet_id
}

data "yandex_compute_image" "ubuntu" {
  family = var.image_family
}

# Виртуальная машина
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

  network_interface {
    subnet_id          = var.subnet_id
    nat                = true
    security_group_ids = [var.security_group_id]
  }

  metadata = {
    # Формат: "имя_пользователя:открытый_ключ"
    ssh-keys = "ubuntu:${var.ssh_public_key}"
  }
}

# Управляемый PostgreSQL
resource "yandex_mdb_postgresql_cluster" "postgres" {
  name        = "quizbot-postgres"
  environment = "PRESTABLE"
  network_id  = data.yandex_vpc_subnet.main.network_id

  # Security group применяется на уровне кластера
  security_group_ids = [var.security_group_id]

  config {
    version = "15"
    resources {
      resource_preset_id = "s2.micro"
      disk_type_id       = "network-ssd"
      disk_size          = 10 # ГБ, управляемый диск создаётся автоматически
    }
  }

  host {
    zone      = data.yandex_vpc_subnet.main.zone
    subnet_id = var.subnet_id
    # assign_public_ip = false (по умолчанию) - доступ только из внутренней сети
  }
}

# Создаем пользователя
resource "yandex_mdb_postgresql_user" "quizbot_user" {
  cluster_id = yandex_mdb_postgresql_cluster.postgres.id
  name       = "quizbot_user"
  password   = var.postgres_password
}

# Создаем базу данных с владельцем
resource "yandex_mdb_postgresql_database" "quizbot_db" {
  cluster_id = yandex_mdb_postgresql_cluster.postgres.id
  name       = "quizbot"
  owner      = "quizbot_user"
  
  depends_on = [yandex_mdb_postgresql_user.quizbot_user]
}