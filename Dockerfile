FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp

# Copiar o JAR já compilado (assumindo que foi compilado com 'mvn clean package')
COPY target/vault-rotation-0.0.1-SNAPSHOT.jar /app/app.jar

# Variáveis de ambiente para configuração do Vault
ENV VAULT_ADDR=http://vault:8200
ENV VAULT_TOKEN=hvs.EXAMPLE-TOKEN-VALUE
ENV DATABASE_URL=jdbc:mysql://mysql:3306/payments

ENTRYPOINT ["java", "-jar", "/app/app.jar"] 