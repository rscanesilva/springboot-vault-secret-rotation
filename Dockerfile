FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /workspace/app

# Copiar o arquivo pom.xml e os arquivos de fonte
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

# Compilar a aplicação
RUN ./mvnw install -DskipTests
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
ARG DEPENDENCY=/workspace/app/target/dependency

# Copiar dependências em camadas para otimização do cache Docker
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

# Variáveis de ambiente para configuração do Vault
ENV VAULT_ADDR=http://vault:8200
ENV VAULT_TOKEN=hvs.EXAMPLE-TOKEN-VALUE
ENV DATABASE_URL=jdbc:mysql://mysql:3306/payments

ENTRYPOINT ["java", "-cp", "app:app/lib/*", "com.example.vaultrotation.VaultRotationApplication"] 