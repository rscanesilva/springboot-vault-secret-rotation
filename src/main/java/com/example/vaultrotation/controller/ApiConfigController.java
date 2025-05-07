package com.example.vaultrotation.controller;

import com.example.vaultrotation.config.ExternalApiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Slf4j
public class ApiConfigController {

    private final ExternalApiConfig externalApiConfig;

    /**
     * Exibe as configurações atuais da API externa
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getExternalApiConfig() {
        log.info("Obtendo configurações atuais da API externa");
        
        Map<String, Object> config = new HashMap<>();
        config.put("url", externalApiConfig.getUrl());
        config.put("apiKey", maskApiKey(externalApiConfig.getApiKey()));
        config.put("timeout", externalApiConfig.getTimeout());
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * Força uma atualização das configurações a partir do Vault
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshExternalApiConfig() {
        log.info("Solicitação de atualização manual das configurações da API externa");
        
        Set<String> refreshedKeys = externalApiConfig.forceRefresh();
        
        Map<String, Object> response = new HashMap<>();
        response.put("refreshed", !refreshedKeys.isEmpty());
        response.put("refreshedKeys", refreshedKeys);
        response.put("newConfig", getExternalApiConfig().getBody());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Mascara a chave da API para não exibir completamente
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        
        // Mostra apenas os primeiros 4 e últimos 4 caracteres
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
} 