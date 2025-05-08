terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.25.0"
    }
    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.25.0"
    }
    docker = {
      source  = "kreuzwerker/docker"
      version = "~> 3.0.2"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2.2"
    }
  }
  required_version = ">= 1.0.0"
}

provider "kubernetes" {
  config_path = var.kube_config_path
}

provider "vault" {
  address = var.vault_addr
  token   = var.vault_token
}

provider "docker" {}

# Módulo para configurar o MySQL no Docker
module "mysql" {
  source = "./modules/mysql"

  mysql_root_password = var.mysql_root_password
  mysql_database      = var.database_name
}

# Módulo para configurar o Vault
module "vault" {
  source = "./modules/vault"

  vault_addr         = var.vault_addr
  vault_token        = var.vault_token
  mysql_host         = module.mysql.mysql_host
  mysql_port         = module.mysql.mysql_port
  mysql_username     = module.mysql.mysql_username
  mysql_password     = module.mysql.mysql_password
  mysql_database     = module.mysql.mysql_database
  vault_role_name    = var.vault_role_name
  vault_max_ttl      = var.vault_max_ttl
  vault_default_ttl  = var.vault_default_ttl
  external_api_url   = var.external_api_url
  external_api_key   = var.external_api_key
  external_api_timeout = var.external_api_timeout

  depends_on = [module.mysql]
}

# Módulo para implantar a aplicação no Kubernetes
module "kubernetes" {
  source = "./modules/kubernetes"

  app_namespace       = var.app_namespace
  app_name            = var.app_name
  app_image           = var.app_image
  vault_addr          = var.vault_addr
  vault_token         = var.vault_token
  vault_role_name     = var.vault_role_name
  database_host       = module.mysql.mysql_host
  database_port       = module.mysql.mysql_port
  database_name       = module.mysql.mysql_database
  replicas            = var.app_replicas

  depends_on = [module.vault]
} 