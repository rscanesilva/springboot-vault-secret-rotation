variable "app_namespace" {
  description = "Namespace para a aplicação no Kubernetes"
  type        = string
}

variable "app_name" {
  description = "Nome da aplicação"
  type        = string
}

variable "app_image" {
  description = "Imagem Docker da aplicação"
  type        = string
}

variable "vault_addr" {
  description = "Endereço do servidor Vault"
  type        = string
}

variable "vault_token" {
  description = "Token de autenticação do Vault"
  type        = string
  sensitive   = true
}

variable "vault_role_name" {
  description = "Nome da role no Vault para geração de credenciais"
  type        = string
}

variable "database_host" {
  description = "Endereço do host do banco de dados"
  type        = string
}

variable "database_port" {
  description = "Porta do banco de dados"
  type        = number
}

variable "database_name" {
  description = "Nome do banco de dados"
  type        = string
}

variable "replicas" {
  description = "Número de réplicas da aplicação"
  type        = number
  default     = 1
} 