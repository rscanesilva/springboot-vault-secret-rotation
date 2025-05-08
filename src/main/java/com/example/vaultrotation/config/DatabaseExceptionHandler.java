package com.example.vaultrotation.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler global para exceções relacionadas ao banco de dados.
 * Intercepta exceções relacionadas a DB e tenta acionar a rotação de credenciais quando apropriado.
 */
@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class DatabaseExceptionHandler {

    private final DatabaseConnectionEventListener connectionEventListener;

    @ExceptionHandler(value = {DataAccessException.class, SQLException.class})
    protected ResponseEntity<Object> handleDataAccessException(Exception ex, WebRequest request) {
        log.error("Erro ao acessar banco de dados: {}", ex.getMessage());
        
        // Notificar o listener de eventos de conexão sobre o erro
        connectionEventListener.handleConnectionError(ex);
        
        // Preparar resposta para o cliente
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("message", "Um erro de banco de dados ocorreu e estamos tentando reconectar. Por favor, tente novamente em alguns instantes.");
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Database Connection Error");
        
        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
    }
} 