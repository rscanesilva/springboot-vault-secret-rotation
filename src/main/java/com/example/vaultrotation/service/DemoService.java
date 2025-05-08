package com.example.vaultrotation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@Slf4j
public class DemoService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Value("${spring.datasource.username}")
    private String username;
    
    /**
     * Retorna dados de demonstração, incluindo informações sobre a conexão com o banco de dados
     * @return Mapa com dados de demonstração
     */
    public Map<String, Object> getDemoData() {
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("username", username);
        
        try {
            // Executar uma consulta simples para verificar a conexão
            String result = jdbcTemplate.queryForObject("SELECT 'Conexão OK'", String.class);
            data.put("connection_status", result);
            data.put("random_number", new Random().nextInt(1000));
            data.put("success", true);
        } catch (Exception e) {
            log.error("Erro ao acessar o banco de dados: {}", e.getMessage(), e);
            data.put("connection_status", "Erro: " + e.getMessage());
            data.put("success", false);
        }
        
        return data;
    }
} 