package com.example.vaultrotation.repository;

import com.example.vaultrotation.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
} 