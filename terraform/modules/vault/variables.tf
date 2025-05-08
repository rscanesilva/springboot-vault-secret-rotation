variable "vault_addr" {
  description = "Endereço do servidor Vault"
  type        = string
}

variable "vault_token" {
  description = "Token de autenticação do Vault"
  type        = string
  sensitive   = true
}

variable "mysql_host" {
  description = "Endereço do host MySQL"
  type        = string
}

variable "mysql_port" {
  description = "Porta do MySQL"
  type        = number
}

variable "mysql_username" {
  description = "Nome de usuário para o MySQL"
  type        = string
}

variable "mysql_password" {
  description = "Senha para o MySQL"
  type        = string
  sensitive   = true
}

variable "mysql_database" {
  description = "Nome do banco de dados MySQL"
  type        = string
}

variable "vault_role_name" {
  description = "Nome da role para credenciais dinâmicas"
  type        = string
}

variable "vault_default_ttl" {
  description = "TTL padrão para credenciais dinâmicas"
  type        = string
}

variable "vault_max_ttl" {
  description = "TTL máximo para credenciais dinâmicas"
  type        = string
}

variable "external_api_url" {
  description = "URL da API externa"
  type        = string
}

variable "external_api_key" {
  description = "Chave de autenticação para API externa"
  type        = string
  sensitive   = true
}

variable "external_api_timeout" {
  description = "Timeout em milissegundos para API externa"
  type        = number
} 