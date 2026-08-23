package com.wydad.digital.payment.service;

import com.wydad.digital.payment.dto.*;
import com.wydad.digital.payment.model.ECashAccount;
import com.wydad.digital.payment.model.Transaction;
import com.wydad.digital.payment.model.TransactionType;
import com.wydad.digital.payment.repository.ECashAccountRepository;
import com.wydad.digital.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wydad.digital.payment.dto.CardPaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ECashAccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RecuFiscalService recuFiscalService;
    private final ChariBaasService chariBaasService;

    @Transactional
    public ECashAccount getOrCreateAccount(String email) {
        return accountRepository.findByEmail(email)
                .orElseGet(() -> accountRepository.save(
                        ECashAccount.builder()
                                .email(email)
                                .balance(BigDecimal.ZERO)
                                .active(true)
                                .build()
                ));
    }

    @Transactional
    public TransactionResponse credit(CreditRequest request) {
        ECashAccount account = getOrCreateAccount(request.email());
        account.setBalance(account.getBalance().add(request.amount()));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .email(request.email())
                .type(TransactionType.CREDIT)
                .amount(request.amount())
                .balanceAfter(account.getBalance())
                .description(request.description() != null ? request.description() : "Crédit E-cash")
                .reference("WAC-CREDIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

        transactionRepository.save(tx);
        return mapToResponse(tx);
    }

    @Transactional
    public TransactionResponse debit(String email, BigDecimal amount, String description) {
        ECashAccount account = getOrCreateAccount(email);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Solde insuffisant");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .email(email)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .description(description != null ? description : "Débit E-cash")
                .reference("WAC-DEBIT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

        transactionRepository.save(tx);
        return mapToResponse(tx);
    }

    /**
     * Débit interne service-à-service (billetterie, boutique) sous verrou
     * pessimiste : sérialise les débits concurrents sur un même compte et
     * garantit qu'aucun solde négatif n'est possible.
     */
    @Transactional
    public TransactionResponse internalDebit(String email, BigDecimal amount, String reference) {
        ECashAccount account = accountRepository.findByEmailForUpdate(email)
                .orElseGet(() -> {
                    ECashAccount created = ECashAccount.builder()
                            .email(email)
                            .balance(BigDecimal.ZERO)
                            .active(true)
                            .build();
                    return accountRepository.save(created);
                });

        if (account.getBalance().compareTo(amount) < 0) {
            throw new com.wydad.digital.payment.exception.InsufficientFundsException(
                    "Solde E-cash insuffisant (disponible : " + account.getBalance() + " DH)");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .email(email)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .description(reference)
                .reference(reference)
                .build();

        transactionRepository.save(tx);
        return mapToResponse(tx);
    }

    /**
     * Remboursement interne service-à-service (annulation de billet)
     * sous verrou pessimiste : recrédite le compte et journalise une
     * transaction typée REFUND, distincte d'un crédit manuel.
     */
    @Transactional
    public TransactionResponse internalRefund(String email, BigDecimal amount, String reference) {
        ECashAccount account = accountRepository.findByEmailForUpdate(email)
                .orElseGet(() -> {
                    ECashAccount created = ECashAccount.builder()
                            .email(email)
                            .balance(BigDecimal.ZERO)
                            .active(true)
                            .build();
                    return accountRepository.save(created);
                });

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .email(email)
                .type(TransactionType.REFUND)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .description(reference)
                .reference(reference)
                .build();

        transactionRepository.save(tx);
        return mapToResponse(tx);
    }

    public BalanceResponse getBalance(String email) {
        ECashAccount account = getOrCreateAccount(email);
        return new BalanceResponse(account.getEmail(), account.getBalance(), account.getUpdatedAt());
    }

    public List<TransactionResponse> getTransactions(String email) {
        return transactionRepository.findByEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public byte[] don(DonRequest request) {
        ECashAccount account = getOrCreateAccount(request.email());

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new RuntimeException("Solde E-cash insuffisant pour le don");
        }

        account.setBalance(account.getBalance().subtract(request.amount()));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .email(request.email())
                .type(TransactionType.DON)
                .amount(request.amount())
                .balanceAfter(account.getBalance())
                .description("Don " + request.type() + " au Wydad AC" + (request.message() != null ? " - " + request.message() : ""))
                .reference("WAC-DON-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

        transactionRepository.save(tx);

        if (request.recuFiscal()) {
            return recuFiscalService.generateRecu(tx, request);
        }
        return null;
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getEmail(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getDescription(),
                tx.getReference(),
                tx.getCreatedAt()
        );
    }
    @Transactional
    public TransactionResponse payByCard(String email, CardPaymentRequest request) {
        CardPaymentResponse payment = chariBaasService.processPayment(request, request.amount());

        if (!payment.isSuccess()) {
            throw new RuntimeException(payment.getMessage());
        }

        ECashAccount account = getOrCreateAccount(email);
        account.setBalance(account.getBalance().add(request.amount()));
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .email(email)
                .type(TransactionType.CREDIT)
                .amount(request.amount())
                .balanceAfter(account.getBalance())
                .description("Crédit par carte bancaire (ChariBaaS) - " + payment.getTransactionId())
                .reference(payment.getTransactionId())
                .build();

        transactionRepository.save(tx);
        return mapToResponse(tx);
    }
}