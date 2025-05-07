#!/bin/bash

# Parar script se ocorrer algum erro
set -e

echo "Iniciando o processo de implantação..."

# Construir o projeto com Maven
echo "Construindo o projeto com Maven..."
./mvnw clean package -DskipTests

# Construir a imagem Docker
echo "Construindo a imagem Docker..."
eval $(minikube docker-env)
docker build -t vaultrotation:0.0.1-SNAPSHOT .

# Verificar se o namespace existe, se não, criar
if ! kubectl get namespace vault-rotation-demo > /dev/null 2>&1; then
  echo "Criando namespace vault-rotation-demo..."
  kubectl apply -f k8s/namespace.yaml
fi

# Solicitar o token do Vault ao usuário
read -p "Informe o token do Vault: " VAULT_TOKEN
ENCODED_TOKEN=$(echo -n "$VAULT_TOKEN" | base64)

# Criar arquivo Secret temporário
echo "Criando Secret do token do Vault..."
sed "s/REPLACE_WITH_BASE64_ENCODED_TOKEN/$ENCODED_TOKEN/g" k8s/secret-template.yaml > k8s/secret.yaml
kubectl apply -f k8s/secret.yaml
rm k8s/secret.yaml  # Remover arquivo temporário

# Aplicar ConfigMap
echo "Aplicando ConfigMap..."
kubectl apply -f k8s/configmap.yaml

# Aplicar Service
echo "Aplicando Service..."
kubectl apply -f k8s/service.yaml

# Aplicar Deployment
echo "Aplicando Deployment..."
kubectl apply -f k8s/deployment.yaml

echo "Aguardando o pod estar pronto..."
kubectl wait --namespace vault-rotation-demo \
  --for=condition=ready pod \
  --selector=app=vault-rotation-app \
  --timeout=120s

echo "Implantação concluída com sucesso!"
echo "Para acessar a aplicação, execute: minikube service vault-rotation-app -n vault-rotation-demo"

# Mostrar informações do pod
kubectl get pods -n vault-rotation-demo 