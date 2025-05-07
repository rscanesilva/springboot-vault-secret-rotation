package com.example.vaultrotation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

@Component
@Slf4j
@EnableScheduling
public class VaultRefresher implements ApplicationListener<RefreshScopeRefreshedEvent> {

    private final ContextRefresher contextRefresher;
    private final SecretLeaseContainer leaseContainer;
    private final String databaseRole;
    private final String databaseBackend;

    public VaultRefresher(
            ContextRefresher contextRefresher,
            SecretLeaseContainer leaseContainer,
            @Value("${spring.cloud.vault.database.role}") String databaseRole,
            @Value("${spring.cloud.vault.database.backend}") String databaseBackend) {
        
        this.contextRefresher = contextRefresher;
        this.leaseContainer = leaseContainer;
        this.databaseRole = databaseRole;
        this.databaseBackend = databaseBackend;
        
        String path = String.format("%s/creds/%s", databaseBackend, databaseRole);
        log.info("Configurando monitoramento de rotação de segredos para o caminho: {}", path);
        
        // Adicionar listener para eventos de lease
        leaseContainer.addLeaseListener(event -> {
            if (path.equals(event.getSource().getPath())) {
                // Quando a credencial expirar, solicita uma rotação
                if (event instanceof SecretLeaseExpiredEvent && event.getSource().getMode() == RequestedSecret.Mode.RENEW) {
                    log.info("Lease de credencial do banco de dados expirada, solicitando rotação");
                    leaseContainer.requestRotatingSecret(path);
                }
                
                // Quando a credencial for criada após rotação, atualiza o contexto
                if (event instanceof SecretLeaseCreatedEvent secretLeaseCreatedEvent && 
                    event.getSource().getMode() == RequestedSecret.Mode.ROTATE) {
                    log.info("Novas credenciais obtidas, atualizando contexto da aplicação");
                    
                    // Disparar atualização de contexto
                    refreshContext();
                }
            }
        });
    }
    
    private void refreshContext() {
        log.info("Atualizando contexto para utilizar novas credenciais do banco de dados");
        contextRefresher.refresh();
    }

    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
        log.info("Contexto atualizado com sucesso: {}", event.getName());
    }
} 