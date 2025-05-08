output "mysql_host" {
  description = "Endereço do host MySQL"
  value       = "host.minikube.internal"
}

output "mysql_port" {
  description = "Porta do MySQL"
  value       = 3306
}

output "mysql_username" {
  description = "Nome de usuário do MySQL"
  value       = "root"
}

output "mysql_password" {
  description = "Senha do MySQL"
  value       = var.mysql_root_password
  sensitive   = true
}

output "mysql_database" {
  description = "Nome do banco de dados"
  value       = var.mysql_database
} 