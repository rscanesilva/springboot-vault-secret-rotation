package com.example.vaultrotation.controller;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/db")
@Slf4j
@RequiredArgsConstructor
public class DatabaseInfoController {

    private final DataSource dataSource;
    private final ContextRefresher contextRefresher;
    
    @Autowired(required = false)
    private SecretLeaseContainer leaseContainer;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getDatabaseInfo() {
        log.info("Obtendo informações do banco de dados");
        
        Map<String, Object> info = new HashMap<>();
        
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            info.put("url", hikariDataSource.getJdbcUrl());
            info.put("username", hikariDataSource.getUsername());
            info.put("password", "******"); // Não exibe a senha por segurança
            info.put("driverClassName", hikariDataSource.getDriverClassName());
            info.put("maxLifetime", hikariDataSource.getMaxLifetime());
            info.put("connectionTimeout", hikariDataSource.getConnectionTimeout());
        } else {
            info.put("dataSourceClass", dataSource.getClass().getName());
        }
        
        // Verifica conexão
        try (Connection connection = dataSource.getConnection()) {
            info.put("connectionValid", connection.isValid(1000));
            info.put("catalogName", connection.getCatalog());
        } catch (SQLException e) {
            log.error("Erro ao verificar conexão: {}", e.getMessage(), e);
            info.put("connectionError", e.getMessage());
        }
        
        info.put("timestamp", LocalDateTime.now().toString());
        info.put("usingVault", leaseContainer != null);
        
        return ResponseEntity.ok(info);
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshDatabaseCredentials() {
        log.info("Solicitação manual de atualização de credenciais");
        
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        
        if (leaseContainer != null) {
            try {
                String path = "database/creds/payments-app";
                log.info("Solicitando rotação para: {}", path);
                
                leaseContainer.requestRotatingSecret(path);
                response.put("rotationRequested", true);
                
                log.info("Atualizando contexto");
                Set<String> refreshedKeys = contextRefresher.refresh();
                
                response.put("refreshedKeys", refreshedKeys);
                response.put("refreshedKeysCount", refreshedKeys.size());
                
                // Obter informações atualizadas
                Map<String, Object> updatedInfo = getDatabaseInfo().getBody();
                response.put("updatedInfo", updatedInfo);
                
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Erro ao solicitar rotação: {}", e.getMessage(), e);
                response.put("error", e.getMessage());
                return ResponseEntity.internalServerError().body(response);
            }
        } else {
            response.put("error", "SecretLeaseContainer não está disponível. Vault não está configurado.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 