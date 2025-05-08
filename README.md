# Spring Boot com Rotação de Segredos do Vault para MySQL

Este projeto demonstra como implementar a rotação dinâmica de credenciais de banco de dados usando HashiCorp Vault em uma aplicação Spring Boot. O processo de rotação de segredos permite que sua aplicação utilize credenciais temporárias para se conectar ao banco de dados MySQL, aumentando a segurança ao eliminar credenciais estáticas de longo prazo.

## Funcionalidades Principais

- **Rotação Dinâmica de Credenciais de Banco de Dados**: Utiliza o Vault para gerar credenciais temporárias para o MySQL
- **Monitoramento de Saúde da Conexão**: Verifica periodicamente a saúde da conexão com o banco de dados e força a rotação em caso de problemas
- **Atualização Automática de Configurações**: Atualiza propriedades da aplicação sem necessidade de reiniciar quando secrets KV são alteradas no Vault
- **Resiliência a Falhas**: Implementa mecanismos para lidar com falhas de conexão e renovação automática de leases (arrendamentos)
- **Limpeza de Conexões**: Gerencia corretamente a limpeza de conexões antigas durante a rotação de credenciais
- **Monitoramento de Usuários Vault**: Rastreia usuários criados pelo Vault para facilitar o gerenciamento

## Pré-requisitos

- Java 17+
- Docker
- Minikube ou outro cluster Kubernetes
- MySQL 8 executando em um contêiner Docker ou localmente
- HashiCorp Vault executando no Minikube

## Arquitetura da Solução

### Componentes Principais

1. **Spring Cloud Vault**: Integração com o Vault para obtenção e renovação de secrets
2. **ConnectionHealthMonitor**: Monitora a saúde das conexões e força a rotação quando necessário
3. **VaultRefresher**: Programado para solicitar novas credenciais periodicamente
4. **DatabaseConfig**: Configura o DataSource com capacidade de atualização dinâmica
5. **MySqlUserManager**: Rastreia os usuários criados pelo Vault

### Fluxo de Rotação de Credenciais

1. A aplicação inicia e solicita credenciais ao Vault usando o Spring Cloud Vault
2. O Vault cria um usuário temporário no MySQL e retorna as credenciais
3. A aplicação configura o DataSource com as credenciais recebidas
4. O `VaultRefresher` programa a solicitação de novas credenciais antes da expiração
5. Quando novas credenciais são recebidas, o DataSource é recriado com as novas credenciais
6. As conexões antigas são fechadas graciosamente e novas conexões são estabelecidas

### Atualizações Automáticas de Secrets KV

As secrets armazenadas no KV (Key-Value) do Vault são acessadas pela aplicação através do Spring Cloud Vault. A atualização automática dessas secrets funciona da seguinte forma:

1. As properties são definidas no caminho `kv/vault-rotation` no Vault
2. A aplicação carrega estas propriedades durante a inicialização
3. O context refresh é acionado periodicamente ou manualmente
4. As alterações nas secrets são detectadas e aplicadas à aplicação sem reiniciar

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
export VAULT_TOKEN=root  # No modo dev, o token é "root"

# Acessar o shell do Vault
kubectl exec -it vault-0 -- /bin/sh

# Habilitar o mecanismo de segredos do banco de dados
vault secrets enable database

# Configurar a conexão com o MySQL
vault write database/config/mysql \
    plugin_name=mysql-database-plugin \
    connection_url="{{username}}:{{password}}@tcp(host.minikube.internal:3306)/" \
    allowed_roles="payments-app" \
    username="root" \
    password="rootpassword"

# Criar a role para a aplicação
vault write database/roles/payments-app \
    db_name=mysql \
    creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT ALL PRIVILEGES ON payments.* TO '{{name}}'@'%';" \
    default_ttl="5m" \
    max_ttl="10m"
```

### 3. Configurar Secrets Estáticas (Key-Value)

A aplicação também suporta secrets estáticas que podem ser atualizadas sem reiniciar o pod:

```bash
# Habilitar o mecanismo de segredos Key-Value versão 2
vault secrets enable -version=2 kv

# Adicionar configurações para a aplicação
vault kv put kv/vault-rotation api.external.url=https://api.exemplo.com/v1
vault kv put kv/vault-rotation api.external.apiKey=chave-secreta-123
vault kv put kv/vault-rotation api.external.timeout=10000
```

## Implantação no Kubernetes

Você pode usar os arquivos de configuração fornecidos no repositório para implantar a aplicação:

```bash
# Construir a aplicação
mvn clean package -DskipTests

# Construir a imagem Docker (usando o ambiente Docker do Minikube)
eval $(minikube docker-env)
docker build -t vault-rotation-app:snapshot .

# Aplicar a configuração do Kubernetes
kubectl apply -f k8s/deployment.yaml

# Ou usar o Terraform
cd terraform
terraform init
terraform apply -auto-approve
```

## Configuração da Aplicação

### application.properties

A configuração principal inclui:

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://host.minikube.internal:3306/payments?useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Vault
spring.cloud.vault.enabled=true
spring.cloud.vault.kv.enabled=true
spring.cloud.vault.kv.backend=kv
spring.cloud.vault.kv.default-context=vault-rotation
spring.cloud.vault.database.enabled=true
spring.cloud.vault.database.role=payments-app
spring.cloud.vault.database.backend=database

# Logging
logging.level.com.example.vaultrotation=DEBUG
```

### bootstrap.properties

```properties
# Application
spring.application.name=vault-rotation

# Vault
spring.cloud.vault.uri=http://vault:8200
spring.cloud.vault.token=root
spring.cloud.vault.fail-fast=false
spring.cloud.vault.kv.default-context=vault-rotation
spring.cloud.vault.database.role=payments-app
spring.cloud.vault.database.backend=database
```

## Monitoramento e Depuração

### Logs Importantes

Os seguintes padrões de log são úteis para monitorar a saúde da rotação de credenciais:

- `Utilizando credencial dinâmica do Vault` - indica que novas credenciais foram obtidas
- `Evento de lease recebido: AfterSecretLeaseRenewedEvent` - indica a renovação bem-sucedida do lease
- `Fechando pool de conexões anterior` - indica o fechamento do pool antigo durante a rotação
- `Conexão com o banco de dados estabelecida com sucesso` - indica uma conexão bem-sucedida

### Monitorar Usuários do MySQL

Para listar os usuários criados pelo Vault no MySQL:

```sql
SELECT user FROM mysql.user WHERE user LIKE 'v-%';
```

## Considerações para Produção

- **TTL mais longos**: Configure TTLs mais longos em produção (2-12h) para reduzir o número de rotações
- **Segurança do Vault**: Use autenticação Kubernetes em vez de tokens estáticos
- **Redução de Logs**: Ajuste o nível de log para evitar excesso de informações em produção
- **Monitoramento**: Implemente alertas para falhas na rotação de credenciais
- **Backoff/Retry**: Configure políticas de retry para eventos de falha na rotação
- **Pool de Conexões**: Ajuste os parâmetros do HikariCP para otimizar o gerenciamento de conexões

## Troubleshooting

### Credenciais não estão sendo rotacionadas

Verifique os logs da aplicação:

```bash
kubectl logs -f -n vault-rotation-demo deployment/vault-rotation-app
```

### Problemas com usuários no MySQL

Verifique se há muitos usuários criados pelo Vault:

```sql
SELECT COUNT(*) FROM mysql.user WHERE user LIKE 'v-%';
```

### Mensagens "Vault location [kv/vault-rotation] not resolvable"

Esta mensagem é esperada se você não tiver configurado secrets no caminho KV correspondente. Crie um secret vazio ou desative o backend KV se não estiver em uso.

## Desenvolvimento Local

Para desenvolvimento local, você pode iniciar o Vault e o MySQL usando Docker Compose:

```bash
docker-compose up -d
```

Em seguida, execute a aplicação localmente:

```bash
./mvnw spring-boot:run
``` 