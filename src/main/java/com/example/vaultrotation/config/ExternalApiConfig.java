package com.example.vaultrotation.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * Configuração para API externa com capacidade de atualização dinâmica.
 * A anotação @RefreshScope permite que as propriedades sejam atualizadas quando o contexto é atualizado.
 */
@Configuration
@ConfigurationProperties(prefix = "api.external")
@RefreshScope
@Data
@Slf4j
public class ExternalApiConfig {

    private String url;
    private String apiKey;
    private int timeout = 5000; // valor padrão
    
    private final ContextRefresher contextRefresher;
    
    @Autowired
    public ExternalApiConfig(ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }
    
    /**
     * Agenda uma verificação periódica a cada 5 minutos para atualizar as configurações
     * das APIs externas a partir do Vault.
     */
    @Scheduled(fixedRate = 300000) // 5 minutos
    public void refreshExternalApiConfig() {
        log.info("Verificando atualizações nas configurações de API externa");
        Set<String> refreshedKeys = contextRefresher.refresh();
        
        if (!refreshedKeys.isEmpty()) {
            log.info("Configurações atualizadas: {}", refreshedKeys);
            log.info("Nova URL da API: {}", url);
        }
    }
    
    /**
     * Método utilitário para forçar uma atualização manual das propriedades
     */
    public Set<String> forceRefresh() {
        log.info("Forçando atualização das configurações de API externa");
        Set<String> refreshedKeys = contextRefresher.refresh();
        log.info("Configurações atualizadas: {}", refreshedKeys);
        return refreshedKeys;
    }
} 