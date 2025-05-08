terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23.0"
    }
  }
  required_version = ">= 1.0"
}

provider "kubernetes" {
  config_path    = "~/.kube/config"
  config_context = "minikube"
}

# Namespace
resource "kubernetes_namespace" "vault_rotation_demo" {
  metadata {
    name = "vault-rotation-demo"
  }
}

# ConfigMap
resource "kubernetes_config_map" "vault_rotation_config" {
  metadata {
    name      = "vault-rotation-config"
    namespace = kubernetes_namespace.vault_rotation_demo.metadata[0].name
  }

  data = {
    "application.properties" = file("${path.module}/../src/main/resources/application.properties")
    "bootstrap.properties"   = file("${path.module}/../src/main/resources/bootstrap.properties")
  }
}

# Deployment
resource "kubernetes_deployment" "vault_rotation_app" {
  metadata {
    name      = "vault-rotation-app"
    namespace = kubernetes_namespace.vault_rotation_demo.metadata[0].name
    labels = {
      app = "vault-rotation-app"
    }
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "vault-rotation-app"
      }
    }

    template {
      metadata {
        labels = {
          app = "vault-rotation-app"
        }
      }

      spec {
        container {
          name              = "vault-rotation-app"
          image             = "vault-rotation-app:snapshot"
          image_pull_policy = "Never"

          port {
            container_port = 8080
          }

          env {
            name  = "VAULT_ADDR"
            value = var.vault_addr
          }

          env {
            name  = "VAULT_TOKEN"
            value = var.vault_token
          }

          env {
            name  = "DATABASE_URL"
            value = "${var.mysql_host}:${var.mysql_port}/${var.mysql_database}"
          }

          env {
            name  = "SPRING_DATASOURCE_URL"
            value = "jdbc:mysql://${var.mysql_host}:${var.mysql_port}/${var.mysql_database}?useSSL=false&allowPublicKeyRetrieval=true"
          }

          env {
            name  = "SPRING_DATASOURCE_DRIVER_CLASS_NAME"
            value = "com.mysql.cj.jdbc.Driver"
          }

          env {
            name  = "LOGGING_LEVEL_COM_EXAMPLE_VAULTROTATION"
            value = "DEBUG"
          }

          env {
            name  = "SPRING_CLOUD_VAULT_URI"
            value = var.vault_addr
          }

          env {
            name  = "SPRING_CLOUD_VAULT_TOKEN"
            value = var.vault_token
          }

          env {
            name  = "SPRING_CLOUD_VAULT_DATABASE_ROLE"
            value = "payments-app"
          }

          env {
            name  = "SPRING_CLOUD_VAULT_DATABASE_BACKEND"
            value = "database"
          }

          env {
            name  = "SPRING_CLOUD_VAULT_ENABLED"
            value = "true"
          }

          env {
            name  = "SPRING_CONFIG_IMPORT"
            value = "vault://"
          }

          env {
            name  = "SPRING_CLOUD_VAULT_FAIL_FAST"
            value = "false"
          }

          env {
            name  = "MYSQL_ADMIN_HOST"
            value = "host.minikube.internal"
          }

          env {
            name  = "MYSQL_ADMIN_PORT"
            value = "3306"
          }

          env {
            name  = "MYSQL_ADMIN_USERNAME"
            value = "root"
          }

          env {
            name  = "MYSQL_ADMIN_PASSWORD"
            value = "rootpassword"
          }

          volume_mount {
            name       = "config-volume"
            mount_path = "/app/config"
          }

          liveness_probe {
            http_get {
              path = "/actuator/health"
              port = 8080
            }
            initial_delay_seconds = 60
            period_seconds        = 15
          }

          readiness_probe {
            http_get {
              path = "/actuator/health"
              port = 8080
            }
            initial_delay_seconds = 30
            period_seconds        = 10
          }

          resources {
            limits = {
              cpu    = "1"
              memory = "512Mi"
            }
            requests = {
              cpu    = "0.5"
              memory = "256Mi"
            }
          }
        }

        volume {
          name = "config-volume"
          config_map {
            name = kubernetes_config_map.vault_rotation_config.metadata[0].name
          }
        }
      }
    }
  }
}

# Service
resource "kubernetes_service" "vault_rotation_service" {
  metadata {
    name      = "vault-rotation-service"
    namespace = kubernetes_namespace.vault_rotation_demo.metadata[0].name
  }

  spec {
    selector = {
      app = kubernetes_deployment.vault_rotation_app.spec[0].template[0].metadata[0].labels.app
    }

    port {
      port        = 80
      target_port = 8080
    }

    type = "ClusterIP"
  }
} 