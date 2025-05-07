# Spring Boot com Rotação de Segredos do Vault para MySQL

Este projeto demonstra como implementar a rotação dinâmica de credenciais de banco de dados usando HashiCorp Vault em uma aplicação Spring Boot. O processo de rotação de segredos permite que sua aplicação utilize credenciais temporárias para se conectar ao banco de dados MySQL, aumentando a segurança ao eliminar credenciais estáticas de longo prazo.

## Pré-requisitos

- Java 17+
- Docker
- Minikube ou outro cluster Kubernetes
- MySQL 8 executando em um contêiner Docker
- HashiCorp Vault executando no Minikube

## Configuração do Vault

### 1. Iniciar o Vault no Minikube

```bash
# Adicionar o repositório Helm do HashiCorp
helm repo add hashicorp https://helm.releases.hashicorp.com

# Instalar o Vault em modo de desenvolvimento (apenas para testes)
helm install vault hashicorp/vault --set server.dev.enabled=true
```

### 2. Configurar o Mecanismo de Segredos do Banco de Dados

```bash
# Configurar variáveis de ambiente do Vault
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=seu-token-root  # Obtido ao instalar o Vault

# Habilitar o mecanismo de segredos do banco de dados
vault secrets enable database

# Configurar a conexão com o MySQL
vault write database/config/mysql \
    plugin_name=mysql-database-plugin \
    connection_url="{{username}}:{{password}}@tcp(mysql:3306)/" \
    allowed_roles="payments-app" \
    username="root" \
    password="senha-root-mysql"

# Criar a role para a aplicação
vault write database/roles/payments-app \
    db_name=mysql \
    creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT ALL PRIVILEGES ON payments.* TO '{{name}}'@'%';" \
    default_ttl="1m" \
    max_ttl="2m"
```

### 3. Configurar Secrets Estáticas (Key-Value)

A aplicação também suporta secrets estáticas que podem ser atualizadas sem reiniciar o pod:

```bash
# Habilitar o mecanismo de segredos Key-Value versão 2
vault secrets enable -version=2 kv

# Adicionar configurações de API externa
vault kv put kv/vault-rotation api.external.url=https://api.exemplo.com/v1
vault kv put kv/vault-rotation api.external.apiKey=chave-secreta-123
vault kv put kv/vault-rotation api.external.timeout=10000
```

### 4. Testar a geração de credenciais

```bash
vault read database/creds/payments-app
```

## Configuração do MySQL

### 1. Criar o Banco de Dados

```bash
# Conectar ao MySQL
mysql -h localhost -P 3306 -u root -p

# Criar o banco de dados
CREATE DATABASE payments;

# Criar a tabela de pagamentos
USE payments;
CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    cc_info VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

## Construção e Implantação

### 1. Construir o Projeto

```bash
./mvnw clean package
```

### 2. Construir a Imagem Docker

```bash
docker build -t vaultrotation:0.0.1-SNAPSHOT .
```

### 3. Implantação no Minikube

Você pode usar o script de implantação fornecido:

```bash
./deploy.sh
```

O script irá:
1. Construir o projeto com Maven
2. Construir a imagem Docker
3. Criar o namespace no Kubernetes (se não existir)
4. Solicitar o token do Vault
5. Criar e aplicar os recursos do Kubernetes (Secret, ConfigMap, Service, Deployment)

## Uso da Aplicação

### 1. Acessar a Aplicação

```bash
minikube service vault-rotation-app -n vault-rotation-demo
```

### 2. Endpoints Disponíveis

- `GET /api/payments` - Listar todos os pagamentos
- `POST /api/payments` - Criar um novo pagamento
- `GET /api/payments/{id}` - Obter um pagamento por ID
- `DELETE /api/payments/{id}` - Excluir um pagamento

### 3. Monitorar a Rotação de Credenciais

- `GET /api/db-info` - Obter informações sobre a conexão do banco de dados atual
- `POST /api/db-info/refresh` - Forçar uma atualização manual do contexto da aplicação

### 4. Gerenciar Configurações de API Externa

- `GET /api/config` - Obter configurações atuais da API externa
- `POST /api/config/refresh` - Forçar uma atualização das configurações a partir do Vault

## Como Atualizar Secrets Estáticas

Você pode atualizar secrets no Vault e a aplicação detectará as mudanças sem necessidade de reiniciar:

```bash
# Atualizar a URL da API externa
vault kv put kv/vault-rotation api.external.url=https://api-nova.exemplo.com/v2

# Verificar a atualização
curl -X POST http://localhost:8080/api/config/refresh
```

A aplicação verifica atualizações automáticas a cada 5 minutos, mas você pode forçar uma atualização imediata com o endpoint `/api/config/refresh`.

## Como a Rotação de Segredos Funciona

1. O Spring Cloud Vault obtém credenciais dinâmicas do Vault ao iniciar a aplicação
2. Um listener monitora os eventos de lease (arrendamento) das credenciais
3. Quando uma credencial está prestes a expirar, a aplicação solicita novas credenciais
4. O bean DataSource é recriado com as novas credenciais
5. As conexões existentes são fechadas e novas conexões são criadas com as novas credenciais
6. Para configurações estáticas, um scheduler verifica periodicamente atualizações

## Considerações para Produção

- Configure TTLs mais longos em produção (por exemplo, horas em vez de minutos)
- Use autenticação mais segura para o Vault, como autenticação Kubernetes
- Implemente monitoria e alertas para falhas na rotação de credenciais
- Configure backoff e retries para eventos de falha na rotação
- Considere usar o HCP Vault em vez de auto-gerenciar o Vault

## Troubleshooting

### Credenciais não estão sendo rotacionadas

Verifique os logs da aplicação para mensagens relacionadas à rotação de credenciais:

```bash
kubectl logs -f -n vault-rotation-demo deployment/vault-rotation-app
```

### Problemas de Conexão com o Vault

Verifique se o Vault está acessível do namespace da aplicação:

```bash
kubectl exec -it -n vault-rotation-demo deployment/vault-rotation-app -- curl -v http://vault.default.svc.cluster.local:8200/v1/sys/health
``` 