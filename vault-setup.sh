#!/bin/bash
set -e

# Variáveis
VAULT_ADDR=${VAULT_ADDR:-http://localhost:8200}
VAULT_TOKEN=${VAULT_TOKEN:-root}
MYSQL_HOST=${MYSQL_HOST:-host.minikube.internal}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_USER=${MYSQL_USER:-root}
MYSQL_PASS=${MYSQL_PASS:-rootpassword}
MYSQL_DB=${MYSQL_DB:-payments}

echo "Configurando Vault em $VAULT_ADDR com token $VAULT_TOKEN para MySQL em $MYSQL_HOST:$MYSQL_PORT"

# Habilitar motor de segredos do banco de dados
echo "Habilitando motor de segredos do banco de dados..."
kubectl exec -n vault vault-0 -- vault secrets enable database || echo "Motor de banco de dados já habilitado"

# Configurar conexão com o MySQL
echo "Configurando conexão com o MySQL..."
kubectl exec -n vault vault-0 -- vault write database/config/payments \
    plugin_name=mysql-database-plugin \
    connection_url="{{username}}:{{password}}@tcp($MYSQL_HOST:$MYSQL_PORT)/" \
    allowed_roles="payments-app" \
    username="$MYSQL_USER" \
    password="$MYSQL_PASS"

# Configurar role para a aplicação com TTL específico
echo "Configurando role payments-app com TTL de 1 hora..."
kubectl exec -n vault vault-0 -- vault write database/roles/payments-app \
    db_name=payments \
    creation_statements="CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'; GRANT ALL PRIVILEGES ON $MYSQL_DB.* TO '{{name}}'@'%';" \
    default_ttl="1h" \
    max_ttl="24h"

# Habilitar motor de secrets KV versão 2
echo "Habilitando motor de secrets KV v2..."
kubectl exec -n vault vault-0 -- vault secrets enable -version=2 kv || echo "Motor KV já habilitado"

# Criar política para a aplicação
echo "Criando política vault-rotation-policy..."
cat <<EOF > vault-policy.hcl
# Permissões para ler e renovar credenciais de banco de dados
path "database/creds/payments-app" {
  capabilities = ["read"]
}

# Permissões para renovar leases
path "sys/leases/renew" {
  capabilities = ["update"]
}

# Permissões para o engine KV
path "kv/data/application/*" {
  capabilities = ["read", "list"]
}

# Permissões para renovar seu próprio token
path "auth/token/renew-self" {
  capabilities = ["update"]
}
EOF

kubectl cp vault-policy.hcl vault/vault-0:/tmp/vault-policy.hcl
kubectl exec -n vault vault-0 -- vault policy write vault-rotation-policy /tmp/vault-policy.hcl

# Criar token para a aplicação
echo "Criando token para a aplicação..."
TOKEN=$(kubectl exec -n vault vault-0 -- vault token create -policy=vault-rotation-policy -period=24h | grep "token " | awk '{print $2}')
echo "Token gerado: $TOKEN"

echo "Atualizando secret com o token do Vault..."
kubectl create secret generic vault-token -n vault-rotation-demo --from-literal=token=$TOKEN -o yaml --dry-run=client | kubectl apply -f -

echo "Token Vault criado e armazenado no secret vault-token"
echo "Configuração concluída!" 