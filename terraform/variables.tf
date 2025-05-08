variable "vault_addr" {
  description = "Endereço do Vault"
  type        = string
  default     = "http://localhost:8200"
}

variable "vault_token" {
  description = "Token de autenticação do Vault"
  type        = string
  default     = "root"
  sensitive   = true
}

variable "kube_config_path" {
  description = "Caminho para o arquivo de configuração do Kubernetes"
  type        = string
  default     = "~/.kube/config"
}

variable "app_namespace" {
  description = "Namespace para a aplicação no Kubernetes"
  type        = string
  default     = "vault-rotation-demo"
}

variable "app_name" {
  description = "Nome da aplicação"
  type        = string
  default     = "vault-rotation-app"
}

variable "app_image" {
  description = "Imagem Docker da aplicação"
  type        = string
  default     = "vaultrotation:0.0.1-SNAPSHOT"
}

variable "app_replicas" {
  description = "Número de réplicas da aplicação"
  type        = number
  default     = 1
}

variable "mysql_root_password" {
  description = "Senha do usuário root do MySQL"
  type        = string
  default     = "rootpassword"
  sensitive   = true
}

variable "database_name" {
  description = "Nome do banco de dados"
  type        = string
  default     = "payments"
}

variable "vault_role_name" {
  description = "Nome da role do Vault para credenciais do banco de dados"
  type        = string
  default     = "payments-app"
}

variable "vault_max_ttl" {
  description = "TTL máximo para as credenciais do banco de dados"
  type        = string
  default     = "2m"
}

variable "vault_default_ttl" {
  description = "TTL padrão para as credenciais do banco de dados"
  type        = string
  default     = "1m"
}

variable "external_api_url" {
  description = "URL da API externa"
  type        = string
  default     = "https://api.exemplo.com/v1"
}

variable "external_api_key" {
  description = "Chave da API externa"
  type        = string
  default     = "chave-secreta-123"
  sensitive   = true
}

variable "external_api_timeout" {
  description = "Timeout para API externa em milissegundos"
  type        = number
  default     = 10000
} 