variable "image_tag" {
  description = "Tag da imagem Docker da aplicação"
  type        = string
  default     = "v6"
}

variable "vault_addr" {
  description = "Endereço do servidor Vault"
  type        = string
  default     = "http://vault.vault.svc.cluster.local:8200"
}

variable "vault_token" {
  description = "Token de autenticação do Vault"
  type        = string
  default     = "root"
  sensitive   = true
}

variable "mysql_host" {
  description = "Host do servidor MySQL"
  type        = string
  default     = "host.minikube.internal"
}

variable "mysql_port" {
  description = "Porta do servidor MySQL"
  type        = number
  default     = 3306
}

variable "mysql_database" {
  description = "Nome do banco de dados MySQL"
  type        = string
  default     = "payments"
}

variable "mysql_root_username" {
  description = "Nome de usuário root do MySQL"
  type        = string
  default     = "root"
}

variable "mysql_root_password" {
  description = "Senha do usuário root do MySQL"
  type        = string
  default     = "rootpassword"
  sensitive   = true
} 