package com.example.vaultrotation.controller;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/db-info")
@Slf4j
@RefreshScope
public class DatabaseInfoController {

    private final DataSource dataSource;
    private final DataSourceProperties dataSourceProperties;
    private final ContextRefresher contextRefresher;

    @Autowired
    public DatabaseInfoController(DataSource dataSource, 
                                 DataSourceProperties dataSourceProperties,
                                 ContextRefresher contextRefresher) {
        this.dataSource = dataSource;
        this.dataSourceProperties = dataSourceProperties;
        this.contextRefresher = contextRefresher;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDatabaseInfo() {
        log.info("Solicitação para obter informações de conexão do banco de dados");
        
        Map<String, Object> info = new HashMap<>();
        info.put("url", dataSourceProperties.getUrl());
        info.put("username", dataSourceProperties.getUsername());
        
        // Se estivermos usando HikariCP, podemos obter informações adicionais
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            info.put("connectionPoolActiveConnections", hikariDataSource.getHikariPoolMXBean().getActiveConnections());
            info.put("connectionPoolIdleConnections", hikariDataSource.getHikariPoolMXBean().getIdleConnections());
            info.put("connectionPoolTotalConnections", hikariDataSource.getHikariPoolMXBean().getTotalConnections());
        }
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * Endpoint para forçar uma atualização manual da configuração
     * Útil para testes e situações de emergência
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> forceRefresh() {
        log.info("Forçando atualização manual do contexto da aplicação");
        
        Set<String> refreshedKeys = contextRefresher.refresh();
        Map<String, Object> response = new HashMap<>();
        response.put("refreshed", true);
        response.put("refreshedKeys", refreshedKeys);
        
        return ResponseEntity.ok(response);
    }
} 