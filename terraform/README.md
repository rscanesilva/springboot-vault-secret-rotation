# Terraform para Projeto Spring Boot com Vault e MySQL

Este projeto Terraform automatiza a configuração do Vault, criação do MySQL em Docker e implantação da aplicação Spring Boot no Minikube para implementar a rotação de segredos.

## Pré-requisitos

- Terraform v1.0.0+
- Docker
- Minikube em execução
- kubectl configurado para o Minikube
- Vault acessível (pode estar rodando no Minikube ou externamente)

## Estrutura do Projeto

- `main.tf` - Arquivo principal do Terraform que orquestra todos os módulos
- `variables.tf` - Declaração de todas as variáveis do projeto
- `outputs.tf` - Saídas do Terraform após a execução
- `modules/` - Diretório contendo os módulos específicos:
  - `mysql/` - Configuração do MySQL no Docker
  - `vault/` - Configuração do Vault para rotação de segredos
  - `kubernetes/` - Implantação da aplicação no Kubernetes

## Como Usar

1. Certifique-se de que o Minikube está em execução:
   ```
   minikube status
   ```

2. Certifique-se de que o Vault está em execução e acessível (por exemplo, no namespace `vault`):
   ```
   kubectl port-forward -n vault svc/vault 8200:8200
   ```

3. Construa a imagem da aplicação (a partir do diretório raiz do projeto):
   ```
   ./mvnw clean package -DskipTests
   docker build -t vaultrotation:0.0.1-SNAPSHOT .
   ```

4. Carregue a imagem no Minikube:
   ```
   minikube image load vaultrotation:0.0.1-SNAPSHOT
   ```

5. Inicialize o Terraform:
   ```
   cd terraform
   terraform init
   ```

6. Crie um arquivo `terraform.tfvars` com suas configurações específicas (opcional):
   ```
   vault_addr = "http://localhost:8200"
   vault_token = "root"
   ```

7. Execute o plano do Terraform:
   ```
   terraform plan
   ```

8. Aplique a configuração:
   ```
   terraform apply
   ```

9. Acesse a aplicação:
   ```
   minikube service vault-rotation-app -n vault-rotation-demo
   ```

## Variáveis

| Nome | Descrição | Padrão |
|------|-----------|--------|
| `vault_addr` | Endereço do Vault | `http://localhost:8200` |
| `vault_token` | Token do Vault | `root` |
| `mysql_root_password` | Senha do MySQL | `rootpassword` |
| `database_name` | Nome do banco de dados | `payments` |
| `app_namespace` | Namespace para a aplicação | `vault-rotation-demo` |
| `app_name` | Nome da aplicação | `vault-rotation-app` |
| `app_image` | Imagem Docker da aplicação | `vaultrotation:0.0.1-SNAPSHOT` |
| `app_replicas` | Número de réplicas | `1` |

## Personalização

Você pode personalizar a configuração editando o arquivo `terraform.tfvars` ou fornecendo variáveis na linha de comando:

```
terraform apply -var="vault_token=seu-token" -var="mysql_root_password=sua-senha"
```

## Limpeza

Para remover todos os recursos criados:

```
terraform destroy
``` 