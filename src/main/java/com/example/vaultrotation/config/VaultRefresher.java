package com.example.vaultrotation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.vault.config.VaultProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import java.util.Set;

@Component
@Slf4j
@EnableScheduling
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(SecretLeaseContainer.class)
public class VaultRefresher implements ApplicationListener<RefreshScopeRefreshedEvent> {

    private final ContextRefresher contextRefresher;
    private final SecretLeaseContainer leaseContainer;
    private final String databaseRole;
    private final String databaseBackend;

    public VaultRefresher(
            ContextRefresher contextRefresher,
            SecretLeaseContainer leaseContainer,
            @Value("${spring.cloud.vault.database.role:payments-app}") String databaseRole,
            @Value("${spring.cloud.vault.database.backend:database}") String databaseBackend) {
        
        this.contextRefresher = contextRefresher;
        this.leaseContainer = leaseContainer;
        this.databaseRole = databaseRole;
        this.databaseBackend = databaseBackend;
        
        String path = String.format("%s/creds/%s", databaseBackend, databaseRole);
        log.info("VaultRefresher inicializado para monitorar o caminho: {}", path);
        
        try {
            // Adicionar listener para eventos de lease
            leaseContainer.addLeaseListener(event -> {
                try {
                    log.debug("Evento de lease recebido: {}, caminho: {}", event.getClass().getSimpleName(), event.getSource().getPath());
                    
                    if (path.equals(event.getSource().getPath())) {
                        // Quando a credencial expirar, solicita uma rotação
                        if (event instanceof SecretLeaseExpiredEvent && event.getSource().getMode() == RequestedSecret.Mode.RENEW) {
                            log.info("Lease de credencial do banco de dados expirada no caminho {}, solicitando rotação", path);
                            leaseContainer.requestRotatingSecret(path);
                        }
                        
                        // Quando a credencial for criada após rotação, atualiza o contexto
                        if (event instanceof SecretLeaseCreatedEvent secretLeaseCreatedEvent && 
                            event.getSource().getMode() == RequestedSecret.Mode.ROTATE) {
                            log.info("Novas credenciais obtidas no caminho {}, atualizando contexto da aplicação", path);
                            
                            // Disparar atualização de contexto
                            refreshContext();
                        }
                    }
                } catch (Exception e) {
                    log.error("Erro ao processar evento de lease: {}", e.getMessage(), e);
                }
            });
            
            log.info("Listener para eventos de lease configurado com sucesso");
        } catch (Exception e) {
            log.error("Erro ao configurar listeners para eventos do Vault: {}", e.getMessage(), e);
        }
    }
    
    private void refreshContext() {
        try {
            log.info("Atualizando contexto para utilizar novas credenciais do banco de dados");
            Set<String> refreshedKeys = contextRefresher.refresh();
            log.info("Contexto atualizado, {} propriedades atualizadas: {}", refreshedKeys.size(), refreshedKeys);
        } catch (Exception e) {
            log.error("Erro ao atualizar contexto: {}", e.getMessage(), e);
        }
    }

    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
        log.info("Contexto atualizado com sucesso: {}", event.getName());
    }
    
    /**
     * Verificação periódica para garantir que as credenciais estejam sendo rotacionadas
     */
    @Scheduled(fixedRate = 300000) // A cada 5 minutos
    public void checkCredentials() {
        String path = String.format("%s/creds/%s", databaseBackend, databaseRole);
        log.info("Verificação periódica das credenciais do banco de dados no caminho: {}", path);
        try {
            // Forçar uma renovação de credenciais
            try {
                leaseContainer.requestRotatingSecret(path);
                log.info("Solicitação de novas credenciais enviada com sucesso");
            } catch (Exception e) {
                log.error("Erro ao solicitar rotação de credenciais: {}", e.getMessage(), e);
            }
            
            // Atualizar o contexto
            refreshContext();
        } catch (Exception e) {
            log.error("Erro durante a verificação periódica: {}", e.getMessage(), e);
        }
    }
} 