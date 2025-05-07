package com.example.vaultrotation.controller;

import com.example.vaultrotation.model.Payment;
import com.example.vaultrotation.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping
    public ResponseEntity<List<Payment>> getAllPayments() {
        log.info("Obtendo todos os pagamentos");
        return ResponseEntity.ok(paymentRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody Payment payment) {
        log.info("Criando novo pagamento: {}", payment);
        return ResponseEntity.ok(paymentRepository.save(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPaymentById(@PathVariable String id) {
        log.info("Buscando pagamento por ID: {}", id);
        return paymentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable String id) {
        log.info("Deletando pagamento com ID: {}", id);
        if (paymentRepository.existsById(id)) {
            paymentRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
} 