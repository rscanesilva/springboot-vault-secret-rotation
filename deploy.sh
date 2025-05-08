#!/bin/bash

# Parar script se ocorrer algum erro
set -e

# Cores para mensagens
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Verificar se o JAR já existe
if [ ! -f "target/vault-rotation-0.0.1-SNAPSHOT.jar" ]; then
  echo -e "${RED}==== JAR não encontrado. Por favor, compile manualmente com o comando:${NC}"
  echo -e "./mvnw clean package -DskipTests"
  exit 1
else
  echo -e "${GREEN}==== JAR encontrado. Pulando compilação ====${NC}"
fi

echo -e "${YELLOW}==== Configurando ambiente Docker do Minikube ====${NC}"
eval $(minikube docker-env)

echo -e "${YELLOW}==== Construindo imagem Docker ====${NC}"
docker build -t vault-rotation-app:v6 .

echo -e "${YELLOW}==== Verificando imagem criada ====${NC}"
docker images | grep vault-rotation-app

echo -e "${YELLOW}==== Iniciando implantação com Terraform ====${NC}"
cd terraform

echo -e "${YELLOW}==== Inicializando Terraform ====${NC}"
terraform init

echo -e "${YELLOW}==== Criando plano de execução ====${NC}"
terraform plan -out=tfplan

echo -e "${YELLOW}==== Aplicando configuração ====${NC}"
terraform apply tfplan

echo -e "${GREEN}==== Implantação concluída com sucesso! ====${NC}"
echo -e "${YELLOW}Para verificar os pods:${NC} kubectl get pods -n vault-rotation-demo"
echo -e "${YELLOW}Para acessar os logs:${NC} kubectl logs -n vault-rotation-demo \$(kubectl get pods -n vault-rotation-demo -o jsonpath='{.items[0].metadata.name}')"

cd .. 