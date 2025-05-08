output "mysql_host" {
  description = "Endereço do host MySQL"
  value       = module.mysql.mysql_host
}

output "mysql_port" {
  description = "Porta do MySQL"
  value       = module.mysql.mysql_port
}

output "vault_addr" {
  description = "Endereço do Vault"
  value       = var.vault_addr
}

output "app_url" {
  description = "URL para acessar a aplicação no Minikube"
  value       = "Execute: minikube service ${var.app_name} -n ${var.app_namespace}"
}

output "vault_status" {
  description = "Status da configuração do Vault"
  value       = "Configurado com sucesso. Secret engine 'database' e 'kv' habilitados."
}

output "mysql_status" {
  description = "Status do MySQL"
  value       = "MySQL rodando em ${module.mysql.mysql_host}:${module.mysql.mysql_port} com banco de dados ${module.mysql.mysql_database}"
}

output "kubernetes_status" {
  description = "Status da implantação no Kubernetes"
  value       = "Aplicação ${var.app_name} implantada no namespace ${var.app_namespace}"
} 