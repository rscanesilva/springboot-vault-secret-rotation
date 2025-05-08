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
