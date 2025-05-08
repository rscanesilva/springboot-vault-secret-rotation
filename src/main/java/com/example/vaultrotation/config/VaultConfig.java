package com.example.vaultrotation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.support.SslConfiguration;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultConfig {

    @Value("${spring.cloud.vault.uri:http://vault.vault.svc.cluster.local:8200}")
    private String vaultUri;

    @Value("${spring.cloud.vault.token:dummy}")
    private String vaultToken;

    @Value("${spring.cloud.vault.database.role:payments-app}")
    private String databaseRole;

    @Value("${spring.cloud.vault.database.backend:database}")
    private String databaseBackend;

    @Bean
    public VaultEndpoint vaultEndpoint() throws URISyntaxException {
        log.info("Configurando endpoint Vault com URI: {}", vaultUri);
        VaultEndpoint endpoint = VaultEndpoint.from(new URI(vaultUri));
        return endpoint;
    }

    @Bean
    public ClientAuthentication clientAuthentication() {
        log.info("Configurando autenticação Vault com token");
        // Se o token estiver vazio, use um token fictício
        String token = StringUtils.hasText(vaultToken) ? vaultToken : "dummy-token";
        return new TokenAuthentication(token);
    }

    @Bean
    public VaultTemplate vaultTemplate(VaultEndpoint endpoint, ClientAuthentication clientAuthentication) {
        log.info("Criando VaultTemplate");
        return new VaultTemplate(endpoint, clientAuthentication);
    }

    @Bean
    public SecretLeaseContainer secretLeaseContainer(VaultOperations vaultOperations) {
        log.info("Criando SecretLeaseContainer");
        SecretLeaseContainer container = new SecretLeaseContainer(vaultOperations);
        
        try {
            // Inicialize o container
            container.afterPropertiesSet();
            container.start();
            
            // Adicione o caminho para as credenciais do banco de dados
            String path = String.format("%s/creds/%s", databaseBackend, databaseRole);
            log.info("Configurando monitoramento de credenciais em: {}", path);
            
            // Solicite as credenciais dinâmicas para serem gerenciadas pelo container
            try {
                log.info("Solicitando credenciais em: {}", path);
                container.addRequestedSecret(RequestedSecret.rotating(path));
                log.info("Credenciais adicionadas com sucesso");
            } catch (Exception e) {
                log.error("Erro ao solicitar credenciais do Vault: {}", e.getMessage());
            }
            
            return container;
        } catch (Exception e) {
            log.error("Erro ao inicializar SecretLeaseContainer: {}", e.getMessage());
            
            // Retornar container mesmo com erro, para permitir que a aplicação continue
            return container;
        }
    }
} 