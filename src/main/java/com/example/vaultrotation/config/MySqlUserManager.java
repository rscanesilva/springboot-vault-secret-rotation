package com.example.vaultrotation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Componente responsável por gerenciar usuários do MySQL criados pelo Vault.
 * Monitora apenas o usuário atual em uso.
 */
@Component
@Slf4j
public class MySqlUserManager {

    // Armazena o usuário atual do Vault
    private final AtomicReference<String> currentVaultUser = new AtomicReference<>("");

    /**
     * Atualiza o usuário atual do Vault
     * @param newUsername Novo usuário criado pelo Vault
     */
    public void updateCurrentVaultUser(String newUsername) {
        if (newUsername == null || newUsername.isEmpty() || !newUsername.startsWith("v-")) {
            log.warn("Tentativa de atualizar usuário Vault com valor inválido: {}", newUsername);
            return;
        }

        String previousUser = currentVaultUser.getAndSet(newUsername);
        if (!previousUser.equals(newUsername)) {
            log.info("Usuário Vault atualizado: {} -> {}", previousUser, newUsername);
        }
    }

    /**
     * Retorna o usuário atual do Vault
     * @return Usuário atual
     */
    public String getCurrentVaultUser() {
        return currentVaultUser.get();
    }
} 