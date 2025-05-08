package com.example.vaultrotation.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Componente responsável por monitorar a saúde das conexões com banco de dados
 * e forçar a renovação de credenciais quando problemas persistentes são detectados.
 */
@Component
@Slf4j
public class ConnectionHealthMonitor {

    private static final int MAX_CONSECUTIVE_FAILURES = 2; // Reduzido para reagir mais rápido
    private static final int MAX_ROTATION_ATTEMPTS = 5;
    
    // Flag para indicar que já estamos tentando uma rotação
    private final AtomicBoolean rotationInProgress = new AtomicBoolean(false);

    private final DataSource dataSource;
    private final ContextRefresher contextRefresher;
    private final SecretLeaseContainer leaseContainer;
    
    @Value("${spring.cloud.vault.database.role:payments-app}")
    private String databaseRole;
    
    @Value("${spring.cloud.vault.database.backend:database}")
    private String databaseBackend;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger totalRotationAttempts = new AtomicInteger(0);

    @Autowired
    public ConnectionHealthMonitor(
            DataSource dataSource,
            ContextRefresher contextRefresher,
            SecretLeaseContainer leaseContainer) {
        this.dataSource = dataSource;
        this.contextRefresher = contextRefresher;
        this.leaseContainer = leaseContainer;
        log.info("ConnectionHealthMonitor iniciado");
    }

    /**
     * Verifica a saúde da conexão com o banco de dados e rotaciona credenciais 
     * se necessário.
     * 
     * @return true se a conexão estiver saudável, false caso contrário
     */
    public boolean checkAndRotateIfNeeded() {
        try (Connection conn = dataSource.getConnection()) {
            boolean isValid = conn.isValid(3000); // Reduzido o timeout
            if (isValid) {
                // Conexão está boa, resetar contadores
                if (consecutiveFailures.get() > 0) {
                    log.info("Conexão recuperada após {} falhas consecutivas", consecutiveFailures.get());
                    consecutiveFailures.set(0);
                    rotationInProgress.set(false);
                }
                return true;
            } else {
                log.warn("Conexão inválida durante verificação de saúde");
                handleConnectionIssue("Conexão retornou status inválido");
                return false;
            }
        } catch (SQLException e) {
            log.error("Erro ao verificar saúde da conexão: {}", e.getMessage());
            
            // Verificar especificamente erros de acesso negado
            if (e.getMessage().contains("Access denied") || 
                e.getMessage().contains("acesso negado") || 
                e.getErrorCode() == 1045) {
                log.error("Erro de autenticação detectado: {}", e.getMessage());
                // Force imediatamente uma rotação sem esperar por falhas consecutivas
                forceCredentialRotation();
                return false;
            }
            
            handleConnectionIssue(e.getMessage());
            return false;
        }
    }

    /**
     * Trata um problema de conexão, incrementando contadores e potencialmente
     * forçando a rotação de credenciais.
     */
    private void handleConnectionIssue(String errorMessage) {
        int failures = consecutiveFailures.incrementAndGet();
        log.warn("Falha de conexão #{}: {}", failures, errorMessage);

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            log.error("{} falhas consecutivas detectadas. Tentando forçar rotação de credenciais...", failures);
            forceCredentialRotation();
        }
    }

    /**
     * Força a rotação de credenciais revogando a lease atual e atualizando o contexto.
     * Este método garante que apenas uma rotação seja executada por vez.
     */
    private void forceCredentialRotation() {
        // Evita múltiplas rotações simultâneas
        if (rotationInProgress.compareAndSet(false, true)) {
            int attempts = totalRotationAttempts.incrementAndGet();
            
            if (attempts > MAX_ROTATION_ATTEMPTS) {
                log.error("Máximo de tentativas de rotação ({}) atingido. Desistindo de rotação automática.", MAX_ROTATION_ATTEMPTS);
                rotationInProgress.set(false);
                return;
            }
            
            log.info("Tentativa #{} de rotação forçada de credenciais", attempts);
            
            try {
                // Se o dataSource for um HikariDataSource, force o fechamento do pool
                if (dataSource instanceof HikariDataSource) {
                    try {
                        log.info("Fechando forçadamente o pool HikariCP atual antes de obter novas credenciais");
                        ((HikariDataSource) dataSource).close();
                        log.info("Pool HikariCP fechado com sucesso");
                    } catch (Exception e) {
                        log.warn("Erro ao fechar pool HikariCP: {}", e.getMessage());
                    }
                }
                
                // Passo 1: Solicitar novas credenciais do Vault
                String databasePath = String.format("%s/creds/%s", databaseBackend, databaseRole);
                log.info("Solicitando renovação do secret em: {}", databasePath);
                
                // Solicitar rotação do secret
                leaseContainer.requestRotatingSecret(databasePath);
                
                // Aguardar um breve momento para que o Vault possa processar a solicitação
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                
                // Passo 2: Atualizar contexto da aplicação com novas propriedades
                log.info("Atualizando contexto da aplicação...");
                contextRefresher.refresh();
                
                log.info("Rotação forçada de credenciais concluída com sucesso");
                resetCounters();
            } catch (Exception e) {
                log.error("Erro durante rotação forçada de credenciais: {}", e.getMessage(), e);
                rotationInProgress.set(false);
            }
        } else {
            log.info("Rotação de credenciais já em andamento, ignorando solicitação duplicada");
        }
    }
    
    /**
     * Reseta os contadores após uma rotação bem-sucedida
     */
    private void resetCounters() {
        consecutiveFailures.set(0);
        // Mantemos o totalRotationAttempts para limitar o número total de tentativas
        rotationInProgress.set(false);
    }
    
    /**
     * Método para forçar manualmente uma rotação de credenciais
     * Útil para testes ou para rotação proativa
     */
    public void triggerManualRotation() {
        log.info("Solicitação manual de rotação de credenciais");
        forceCredentialRotation();
    }
} 