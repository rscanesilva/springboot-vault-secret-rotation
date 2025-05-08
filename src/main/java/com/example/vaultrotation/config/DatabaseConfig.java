package com.example.vaultrotation.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
@Slf4j
public class DatabaseConfig implements DisposableBean {

    // Mantém referência global ao dataSource atual para poder fechá-lo corretamente
    private static final AtomicReference<HikariDataSource> currentDataSource = new AtomicReference<>();
    
    // Lock para garantir exclusão mútua durante a criação/fechamento do datasource
    private static final ReentrantLock dataSourceLock = new ReentrantLock();
    
    // Flag para indicar se estamos em um processo de rotação
    private static boolean isRotating = false;
    
    // Armazena as últimas credenciais utilizadas para evitar loops
    private static String lastUsername = "";
    
    @Autowired(required = false)
    private ConnectionHealthMonitor healthMonitor;
    
    @Autowired
    private MySqlUserManager mySqlUserManager;

    @Value("${spring.datasource.url:jdbc:mysql://host.minikube.internal:3306/payments?useSSL=false&allowPublicKeyRetrieval=true}")
    private String url;

    @Value("${spring.datasource.username:root}")
    private String username;

    @Value("${spring.datasource.password:rootpassword}")
    private String password;

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    /**
     * Configuração do DataSource com capacidade de atualização quando as credenciais são rotacionadas.
     * A anotação @RefreshScope permite que este bean seja recriado quando o contexto é atualizado.
     * Ao criar um novo DataSource, o anterior é fechado corretamente.
     */
    @Bean
    @RefreshScope
    @Primary
    public DataSource dataSource() {
        try {
            // Adquirir lock para garantir que apenas um thread crie/feche datasource por vez
            dataSourceLock.lock();
            
            log.info("Criando DataSource com usuário: {}", username);
            log.info("URL de conexão: {}", url);
            log.debug("Driver: {}", driverClassName);
            
            // Verificar se as credenciais são as mesmas da última tentativa
            // Isto evita ciclos infinitos de criação/fechamento do datasource
            if (StringUtils.hasText(lastUsername) && lastUsername.equals(username) && isRotating) {
                log.warn("Tentativa de criar DataSource com mesmas credenciais durante rotação. Isso pode indicar um loop.");
                
                // Se temos um datasource atual e estamos em rotação, retorne o atual
                HikariDataSource existing = currentDataSource.get();
                if (existing != null && !existing.isClosed()) {
                    log.info("Reutilizando datasource existente durante rotação para evitar loop");
                    return existing;
                }
            }
            
            // Atualizar último usuário
            lastUsername = username;
            
            // Sinalizar que estamos em um processo de rotação
            boolean wasRotating = isRotating;
            isRotating = true;
            
            // Detectar se estamos usando credenciais dinâmicas do Vault
            if (username != null && username.startsWith("v-")) {
                log.info("Utilizando credencial dinâmica do Vault. Usuário: {}", username);
                // Registrar o usuário atual no gerenciador de usuários MySQL
                mySqlUserManager.updateCurrentVaultUser(username);
            } else {
                log.info("Utilizando credencial estática para o banco de dados");
            }
            
            // Fechar dataSource anterior se existir
            closeCurrentDataSource();
            
            // Criar novo dataSource
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(url);
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            dataSource.setDriverClassName(driverClassName);
            
            // Configurações recomendadas para rotação de credenciais
            dataSource.setMinimumIdle(1);        // Reduzido para minimizar conexões ociosas
            dataSource.setMaximumPoolSize(5);    // Reduzido para facilitar o gerenciamento
            dataSource.setConnectionTimeout(5000); // 5 segundos para falhar mais rápido
            dataSource.setIdleTimeout(60000);    // 1 minuto de idle
            
            // Para rotação de credenciais, é importante ter um maxLifetime menor que o TTL das credenciais
            // Se o TTL for definido como 1 hora (3600000ms), configure maxLifetime para algo como 50 minutos
            dataSource.setMaxLifetime(1800000);   // 30 minutos
            
            // Configurações críticas para lidar com falhas de conexão
            dataSource.setInitializationFailTimeout(10000); // 10 segundos
            dataSource.setConnectionTestQuery("SELECT 1");
            
            // Configurar validação periódica das conexões 
            dataSource.setValidationTimeout(3000); // 3 segundos
            dataSource.setKeepaliveTime(60000);    // 60 segundos
            dataSource.setLeakDetectionThreshold(60000); // 60 segundos
            
            // Configurar para reconexão automática após falha
            dataSource.setAutoCommit(true);
            dataSource.setConnectionInitSql("SELECT 1");
            dataSource.setRegisterMbeans(true);
            
            // Armazenar a referência ao datasource atual para poder fechá-lo depois
            currentDataSource.set(dataSource);
            
            // Verificar imediatamente se a conexão funciona
            try (Connection conn = dataSource.getConnection()) {
                boolean valid = conn.isValid(5000);
                if (valid) {
                    log.info("Conexão com o banco de dados estabelecida com sucesso utilizando usuário: {}", username);
                    isRotating = false; // Rotação concluída com sucesso
                    return dataSource;
                } else {
                    log.warn("Conexão estabelecida, mas retornou status inválido para usuário: {}", username);
                    if (healthMonitor != null && !wasRotating) {
                        log.info("Solicitando verificação proativa da saúde da conexão");
                        healthMonitor.checkAndRotateIfNeeded();
                    }
                    // Mesmo com status inválido, retornamos o dataSource para evitar falha completa
                    isRotating = false;
                    return dataSource;
                }
            } catch (SQLException e) {
                log.error("Erro ao testar conexão inicial com banco de dados: {}", e.getMessage(), e);
                
                // Se não estávamos já em rotação e temos healthMonitor, solicite uma rotação
                if (healthMonitor != null && !wasRotating && (e.getMessage().contains("Access denied") || e.getErrorCode() == 1045)) {
                    log.info("Erro de acesso na conexão inicial. Solicitando rotação de credenciais...");
                    healthMonitor.triggerManualRotation();
                }
                
                // Temos que retornar algo... mesmo que a conexão tenha falhado inicialmente
                // O pool HikariCP continuará tentando estabelecer conexões
                isRotating = false;
                return dataSource;
            }
        } finally {
            dataSourceLock.unlock();
        }
    }
    
    /**
     * Fecha o datasource atual se existir, com garantia de exclusão mútua
     */
    private void closeCurrentDataSource() {
        HikariDataSource previous = currentDataSource.getAndSet(null);
        if (previous != null) {
            try {
                log.info("Fechando pool de conexões anterior...");
                
                // Primeiro evictar todas as conexões
                if (previous.getHikariPoolMXBean() != null) {
                    previous.getHikariPoolMXBean().softEvictConnections();
                    // Aguardar um pouco para as conexões serem evictadas
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Então fechar o pool
                previous.close();
                log.info("Pool de conexões anterior fechado com sucesso");
            } catch (Exception e) {
                log.warn("Erro ao fechar pool de conexões anterior: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Verifica periodicamente a saúde da conexão com o banco de dados
     * Em caso de falha, um erro será logado e o sistema poderá tomar medidas
     * como solicitar novas credenciais do Vault
     */
    @Scheduled(fixedRate = 30000) // Verificar a cada 30 segundos
    public void checkDatabaseConnection() {
        // Não realizar verificação se estamos em processo de rotação
        if (isRotating) {
            log.debug("Ignorando verificação de saúde durante rotação de credenciais");
            return;
        }
        
        // Primeiramente, tentar usar o healthMonitor se disponível (modo Vault)
        if (healthMonitor != null) {
            log.debug("Iniciando verificação periódica de saúde da conexão usando healthMonitor");
            boolean isHealthy = healthMonitor.checkAndRotateIfNeeded();
            if (isHealthy) {
                log.debug("Verificação de saúde da conexão: OK");
                return; // Se conexão estiver saudável, não precisa fazer verificação adicional
            }
            log.warn("Verificação de saúde da conexão detectou problemas");
        }
        
        // Verificação padrão se não tiver healthMonitor (modo não-Vault)
        HikariDataSource ds = currentDataSource.get();
        if (ds != null) {
            try (Connection conn = ds.getConnection()) {
                boolean valid = conn.isValid(3000);
                if (valid) {
                    log.debug("Verificação de conexão com banco de dados: OK");
                } else {
                    log.warn("Conexão com banco de dados inválida. Pode indicar necessidade de rotação de credenciais.");
                    // Forçar a evicção de conexões inválidas do pool
                    if (ds.getHikariPoolMXBean() != null) {
                        ds.getHikariPoolMXBean().softEvictConnections();
                    }
                }
            } catch (SQLException e) {
                log.error("Erro na verificação de conexão com banco de dados: {}. Pode ser necessário solicitar novas credenciais.", e.getMessage());
                if (healthMonitor != null && (e.getMessage().contains("Access denied") || e.getErrorCode() == 1045)) {
                    log.error("Erro de acesso detectado durante verificação periódica. Solicitando rotação...");
                    healthMonitor.triggerManualRotation();
                }
                // Se não temos healthMonitor mas é um erro de acesso, tente evictar conexões
                else if (e.getMessage().contains("Access denied") || e.getErrorCode() == 1045) {
                    try {
                        log.warn("Detectado erro de acesso. Forçando reset do pool...");
                        if (ds.getHikariPoolMXBean() != null) {
                            ds.getHikariPoolMXBean().softEvictConnections();
                        }
                    } catch (Exception ex) {
                        log.error("Erro ao tentar evictar conexões: {}", ex.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Método chamado quando o bean é destruído para garantir que o pool 
     * de conexões seja fechado corretamente
     */
    @Override
    public void destroy() {
        log.info("Bean DatabaseConfig sendo destruído, fechando pool de conexões...");
        closeCurrentDataSource();
    }
} 