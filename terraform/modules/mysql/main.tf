terraform {
  required_providers {
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0.2"
    }
  }
}

resource "docker_image" "mysql" {
  name = "mysql:8"
  keep_locally = true
}

resource "docker_container" "mysql" {
  name  = "terraform-mysql"
  image = docker_image.mysql.image_id
  
  ports {
    internal = 3306
    external = 3306
  }

  env = [
    "MYSQL_ROOT_PASSWORD=${var.mysql_root_password}",
    "MYSQL_DATABASE=${var.mysql_database}"
  ]

  volumes {
    container_path = "/var/lib/mysql"
    volume_name    = "mysql-data"
  }

  restart = "always"
  
  healthcheck {
    test     = ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${var.mysql_root_password}"]
    interval = "5s"
    timeout  = "3s"
    retries  = 5
  }
}

resource "docker_volume" "mysql_data" {
  name = "mysql-data"
}

# Criar a tabela de pagamentos no banco de dados
resource "null_resource" "setup_database" {
  depends_on = [docker_container.mysql]
  
  provisioner "local-exec" {
    command = <<-EOT
      sleep 20 # Aguardar o MySQL inicializar completamente
      docker exec terraform-mysql mysql -u root -p${var.mysql_root_password} -e "
        USE ${var.mysql_database};
        CREATE TABLE IF NOT EXISTS payments (
          id VARCHAR(36) PRIMARY KEY,
          name VARCHAR(100) NOT NULL,
          cc_info VARCHAR(100) NOT NULL,
          amount DECIMAL(10,2) NOT NULL,
          created_at TIMESTAMP NOT NULL
        );
      "
    EOT
  }
} 