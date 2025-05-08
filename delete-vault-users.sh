#!/bin/bash

echo "Listando usuários Vault no MySQL..."
VAULT_USERS=$(docker exec 74e360080101 mysql -uroot -prootpassword -e "SELECT CONCAT('\'', user, '\'@\'%\'') FROM mysql.user WHERE user LIKE 'v-%';" --skip-column-names 2>/dev/null)

if [ -z "$VAULT_USERS" ]; then
    echo "Nenhum usuário Vault encontrado no MySQL."
    exit 0
fi

echo "Encontrados os seguintes usuários Vault:"
echo "$VAULT_USERS" | tr '\n' ', '
echo

read -p "Tem certeza que deseja remover todos estes usuários? (s/n): " CONFIRM
if [ "$CONFIRM" != "s" ]; then
    echo "Operação cancelada."
    exit 0
fi

echo "Removendo usuários Vault..."
for USER in $VAULT_USERS; do
    echo "Removendo $USER..."
    docker exec 74e360080101 mysql -uroot -prootpassword -e "DROP USER $USER; FLUSH PRIVILEGES;" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✓ Usuário $USER removido com sucesso."
    else
        echo "✗ Erro ao remover usuário $USER."
    fi
done

echo "Verificando se todos os usuários foram removidos..."
REMAINING=$(docker exec 74e360080101 mysql -uroot -prootpassword -e "SELECT COUNT(*) FROM mysql.user WHERE user LIKE 'v-%';" --skip-column-names 2>/dev/null)

if [ "$REMAINING" -eq "0" ]; then
    echo "✓ Todos os usuários Vault foram removidos com sucesso."
else
    echo "! Atenção: Ainda existem $REMAINING usuários Vault no MySQL."
fi 