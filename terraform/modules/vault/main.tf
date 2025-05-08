terraform {
  required_providers {
    vault = {
      source  = "hashicorp/vault"
      version = "~> 3.25.0"
    }
  }
}

# Habilitar o engine de segredos para banco de dados
resource "vault_mount" "db" {
  path = "database"
  type = "database"
  description = "Interface de gerenciamento de credenciais dinâmicas para MySQL"
}

# Habilitar o engine de segredos Key-Value v2
resource "vault_mount" "kv" {
  path = "kv"
  type = "kv"
  description = "Secret engine Key-Value v2 para armazenar configurações de API"
  options = {
    version = "2"
  }
}

# Configurar a conexão com o MySQL
resource "vault_database_secret_backend_connection" "mysql" {
  backend = vault_mount.db.path
  name    = "mysql"
  
  mysql {
    connection_url = "{{username}}:{{password}}@tcp(${var.mysql_host}:${var.mysql_port})/"
    username       = var.mysql_username
    password       = var.mysql_password
    allowed_roles  = [var.vault_role_name]
  }
}

# Criar a role para geração de credenciais
resource "vault_database_secret_backend_role" "role" {
  backend             = vault_mount.db.path
  name                = var.vault_role_name
  db_name             = vault_database_secret_backend_connection.mysql.name
  default_ttl         = var.vault_default_ttl
  max_ttl             = var.vault_max_ttl
  creation_statements = [
    "CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}';",
    "GRANT SELECT, INSERT, UPDATE, DELETE ON ${var.mysql_database}.* TO '{{name}}'@'%';",
  ]
  revocation_statements = [
    "DROP USER IF EXISTS '{{name}}'@'%';",
  ]
}

# Armazenar configurações de API externa no KV
resource "vault_kv_secret_v2" "api_config" {
  mount               = vault_mount.kv.path
  name                = var.vault_role_name
  delete_all_versions = true
  data_json = jsonencode({
    "api.external.url"     = var.external_api_url
    "api.external.apiKey"  = var.external_api_key
    "api.external.timeout" = var.external_api_timeout
  })
} 