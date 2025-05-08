terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25.0"
    }
  }
}

# Criar o namespace para a aplicação
resource "kubernetes_namespace" "app_namespace" {
  metadata {
    name = var.app_namespace
  }
}

# Criar o ConfigMap com as configurações da aplicação
resource "kubernetes_config_map" "app_config" {
  metadata {
    name      = "${var.app_name}-config"
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
  }

  data = {
    "application.properties" = <<-EOT
      # Configurações do servidor
      server.port=8080
      
      # Configurações de JPA
      spring.jpa.hibernate.ddl-auto=update
      spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
      spring.jpa.open-in-view=false
      
      # Configurações de log
      logging.level.org.springframework.cloud.vault=DEBUG
      logging.level.com.example.vaultrotation=DEBUG
      
      # Configuração para mostrar SQL
      spring.jpa.show-sql=true
      spring.jpa.properties.hibernate.format_sql=true
    EOT
    
    "bootstrap.properties" = <<-EOT
      # Nome da aplicação (usado pelo Vault para buscar segredos)
      spring.application.name=vault-rotation
      
      # Configuração do Vault
      spring.cloud.vault.uri=${var.vault_addr}
      spring.cloud.vault.token=${var.vault_token}
      spring.cloud.vault.scheme=http
      spring.cloud.vault.fail-fast=true
      
      # Configuração do ciclo de vida dos segredos
      spring.cloud.vault.config.lifecycle.enabled=true
      spring.cloud.vault.config.lifecycle.min-renewal=10s
      spring.cloud.vault.config.lifecycle.expiry-threshold=1m
      
      # Configuração do banco de dados secretos
      spring.cloud.vault.database.enabled=true
      spring.cloud.vault.database.role=${var.vault_role_name}
      spring.cloud.vault.database.backend=database
      
      # Configuração do backend de key-value para secrets estáticas
      spring.cloud.vault.kv.enabled=true
      spring.cloud.vault.kv.backend=kv
      spring.cloud.vault.kv.default-context=application
      spring.cloud.vault.kv.application-name=vault-rotation
      
      # Importar configuração do Vault
      spring.config.import=vault://
      
      # URL do banco de dados
      spring.datasource.url=jdbc:mysql://${var.database_host}:${var.database_port}/${var.database_name}?useSSL=false&allowPublicKeyRetrieval=true
      
      # Expor endpoint de refresh para atualizações manuais
      management.endpoints.web.exposure.include=refresh,health,info
    EOT
  }
}

# Criar o Secret com o token do Vault
resource "kubernetes_secret" "vault_token" {
  metadata {
    name      = "vault-token"
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
  }

  data = {
    token = var.vault_token
  }

  type = "Opaque"
}

# Criar o Deployment da aplicação
resource "kubernetes_deployment" "app" {
  metadata {
    name      = var.app_name
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
    labels = {
      app = var.app_name
    }
  }

  spec {
    replicas = var.replicas

    selector {
      match_labels = {
        app = var.app_name
      }
    }

    template {
      metadata {
        labels = {
          app = var.app_name
        }
      }

      spec {
        container {
          name  = var.app_name
          image = var.app_image
          
          port {
            container_port = 8080
          }

          env {
            name  = "VAULT_ADDR"
            value = var.vault_addr
          }
          
          env {
            name = "VAULT_TOKEN"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.vault_token.metadata[0].name
                key  = "token"
              }
            }
          }
          
          env {
            name  = "DATABASE_URL"
            value = "jdbc:mysql://${var.database_host}:${var.database_port}/${var.database_name}?useSSL=false&allowPublicKeyRetrieval=true"
          }

          volume_mount {
            name       = "config-volume"
            mount_path = "/app/config"
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
        }
        
        volume {
          name = "config-volume"
          config_map {
            name = kubernetes_config_map.app_config.metadata[0].name
          }
        }
      }
    }
  }

  depends_on = [
    kubernetes_config_map.app_config,
    kubernetes_secret.vault_token,
  ]
}

# Criar o Service para expor a aplicação
resource "kubernetes_service" "app" {
  metadata {
    name      = var.app_name
    namespace = kubernetes_namespace.app_namespace.metadata[0].name
  }

  spec {
    selector = {
      app = var.app_name
    }
    
    port {
      port        = 8080
      target_port = 8080
    }
    
    type = "NodePort"
  }

  depends_on = [
    kubernetes_deployment.app,
  ]
} 