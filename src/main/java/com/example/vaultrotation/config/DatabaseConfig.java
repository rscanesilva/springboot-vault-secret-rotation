package com.example.vaultrotation.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@Slf4j
public class DatabaseConfig {

    /**
     * Configuração do DataSource com capacidade de atualização quando as credenciais são rotacionadas.
     * A anotação @RefreshScope permite que este bean seja recriado quando o contexto é atualizado.
     */
    @Bean
    @RefreshScope
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        log.info("Criando DataSource com usuário: {}", properties.getUsername());
        
        // Usar HikariDataSource para conexões eficientes
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        
        // Configurações recomendadas para rotação de credenciais
        dataSource.setMinimumIdle(2);
        dataSource.setMaximumPoolSize(10);
        dataSource.setConnectionTimeout(5000); // 5 segundos
        dataSource.setIdleTimeout(300000); // 5 minutos
        dataSource.setMaxLifetime(600000); // 10 minutos
        
        return dataSource;
    }
} 