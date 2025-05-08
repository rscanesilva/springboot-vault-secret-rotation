package com.example.vaultrotation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Componente que monitora erros de conexão com o banco de dados e 
 * reage a erros de autenticação para forçar a rotação de credenciais.
 */
@Component
@Slf4j
public class DatabaseConnectionEventListener implements ApplicationListener<ContextRefreshedEvent> {

    private final DataSource dataSource;
    private final ConnectionHealthMonitor healthMonitor;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    @Autowired
    public DatabaseConnectionEventListener(
            DataSource dataSource,
            @Autowired(required = false) ConnectionHealthMonitor healthMonitor) {
        this.dataSource = dataSource;
        this.healthMonitor = healthMonitor;
        
        log.info("DatabaseConnectionEventListener iniciado");
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (initialized.compareAndSet(false, true)) {
            log.info("Contexto inicializado, validando conexão inicial com o banco de dados");
            
            // Verificar conexão inicial ao iniciar a aplicação
            if (healthMonitor != null) {
                log.info("Realizando verificação inicial da conexão com banco de dados");
                healthMonitor.checkAndRotateIfNeeded();
            }
        }
    }
    
    /**
     * Método público que pode ser chamado por outros componentes quando detectarem 
     * problemas de conexão para forçar uma verificação e possível rotação.
     */
    public void handleConnectionError(Exception exception) {
        if (healthMonitor != null) {
            // Verificar se o erro é relacionado a autenticação
            if (isAuthenticationError(exception)) {
                log.error("Erro de autenticação detectado: {}", exception.getMessage());
                healthMonitor.triggerManualRotation();
            } else {
                log.warn("Erro de conexão detectado (não é de autenticação): {}", exception.getMessage());
                healthMonitor.checkAndRotateIfNeeded();
            }
        } else {
            log.warn("Erro de conexão detectado, mas não há healthMonitor disponível: {}", exception.getMessage());
        }
    }
    
    /**
     * Verifica se o erro é relacionado a autenticação
     */
    private boolean isAuthenticationError(Exception e) {
        if (e instanceof SQLException) {
            SQLException sqlException = (SQLException) e;
            return sqlException.getErrorCode() == 1045 || 
                   e.getMessage().contains("Access denied") ||
                   e.getMessage().contains("acesso negado");
        } else if (e instanceof DataAccessException && e.getCause() instanceof SQLException) {
            SQLException sqlException = (SQLException) e.getCause();
            return sqlException.getErrorCode() == 1045 || 
                   e.getMessage().contains("Access denied") ||
                   e.getMessage().contains("acesso negado");
        }
        return false;
    }
} 