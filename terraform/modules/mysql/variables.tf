variable "mysql_root_password" {
  description = "Senha do usuário root do MySQL"
  type        = string
  sensitive   = true
}

variable "mysql_database" {
  description = "Nome do banco de dados a ser criado"
  type        = string
  default     = "payments"
} 