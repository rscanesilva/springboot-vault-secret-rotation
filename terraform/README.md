# Implantação com Terraform

Este diretório contém a configuração do Terraform para implantar a aplicação `vault-rotation-app` no Kubernetes, junto com a configuração do Vault.

## Pré-requisitos

- Terraform v1.0+
- Um cluster Kubernetes em execução (Minikube)
- MySQL instalado e configurado
- Vault em execução no namespace `vault`

## Arquivos

- `main.tf` - Configuração principal do Kubernetes (namespace, deployment, service)
- `vault.tf` - Configuração do Vault (secret engines, roles, policies)
- `variables.tf` - Definição de variáveis para a configuração

## Como usar

1. Certifique-se de que a aplicação Spring Boot foi compilada e a imagem Docker foi criada:
   ```bash
   mvn clean package -DskipTests && eval $(minikube docker-env) && docker build -t vault-rotation-app:v6 .
   ```

2. Inicialize o Terraform:
   ```bash
   cd terraform
   terraform init
   ```

3. Verifique o plano de execução:
   ```bash
   terraform plan
   ```

4. Aplique a configuração:
   ```bash
   terraform apply
   ```

5. Para destruir a infraestrutura quando não for mais necessária:
   ```bash
   terraform destroy
   ```

## Variáveis

Você pode personalizar a implantação modificando as variáveis em `variables.tf` ou fornecendo valores na linha de comando:

```bash
terraform apply -var="image_tag=v7" -var="mysql_root_password=novasenha"
```

## Notas importantes

- A autenticação no Vault é feita usando um token fixo para simplificar. Em ambientes de produção, use métodos de autenticação mais seguros.
- O Terraform tentará ler os arquivos do ServiceAccount do Kubernetes para a autenticação com o Vault. Para ambientes locais, pode ser necessário modificar esta configuração.
- A configuração atual mantém apenas um usuário do Vault no MySQL por vez, removendo os usuários antigos quando novas credenciais são obtidas. 