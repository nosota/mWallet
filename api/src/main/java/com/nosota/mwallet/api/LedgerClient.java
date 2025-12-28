package com.nosota.mwallet.api;

import com.nosota.mwallet.api.dto.PagedResponse;
import com.nosota.mwallet.api.dto.SettlementHistoryDTO;
import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;

/**
 * WebClient-based implementation of LedgerApi for consuming the mWallet service.
 *
 * <p>This client can be used by other services to interact with the mWallet ledger API.
 *
 * <p><b>IMPORTANT:</b> This client is NOT a Spring @Component. Consuming services must
 * manually register it as a bean in their configuration.
 *
 * <p>Configuration example:
 * <pre>
 * {@code
 * @Configuration
 * public class MWalletClientConfig {
 *     @Bean
 *     public WebClient mwalletWebClient(WebClient.Builder builder,
 *                                        @Value("${services.mwallet.url}") String baseUrl) {
 *         return builder.baseUrl(baseUrl).build();
 *     }
 *
 *     @Bean
 *     public LedgerClient ledgerClient(WebClient mwalletWebClient) {
 *         return new LedgerClient(mwalletWebClient);
 *     }
 * }
 * }
 * </pre>
 */
@RequiredArgsConstructor
@Slf4j
public class LedgerClient implements LedgerApi {

    private final WebClient webClient;

    @Override
    public ResponseEntity<TransactionResponse> holdDebit(Integer walletId, Long amount, UUID referenceId) {
        log.debug("Calling holdDebit: walletId={}, amount={}, referenceId={}", walletId, amount, referenceId);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/wallets/{walletId}/hold-debit")
                        .queryParam("amount", amount)
                        .queryParam("referenceId", referenceId)
                        .build(walletId))
                .retrieve()
                .toEntity(TransactionResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionResponse> holdCredit(Integer walletId, Long amount, UUID referenceId) {
        log.debug("Calling holdCredit: walletId={}, amount={}, referenceId={}", walletId, amount, referenceId);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/wallets/{walletId}/hold-credit")
                        .queryParam("amount", amount)
                        .queryParam("referenceId", referenceId)
                        .build(walletId))
                .retrieve()
                .toEntity(TransactionResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionResponse> settle(Integer walletId, UUID referenceId) {
        log.debug("Calling settle: walletId={}, referenceId={}", walletId, referenceId);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/wallets/{walletId}/settle")
                        .queryParam("referenceId", referenceId)
                        .build(walletId))
                .retrieve()
                .toEntity(TransactionResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionResponse> release(Integer walletId, UUID referenceId) {
        log.debug("Calling release: walletId={}, referenceId={}", walletId, referenceId);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/wallets/{walletId}/release")
                        .queryParam("referenceId", referenceId)
                        .build(walletId))
                .retrieve()
                .toEntity(TransactionResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionResponse> cancel(Integer walletId, UUID referenceId) {
        log.debug("Calling cancel: walletId={}, referenceId={}", walletId, referenceId);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/wallets/{walletId}/cancel")
                        .queryParam("referenceId", referenceId)
                        .build(walletId))
                .retrieve()
                .toEntity(TransactionResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> createTransactionGroup() {
        log.debug("Calling createTransactionGroup");

        return webClient.post()
                .uri("/api/v1/ledger/groups")
                .retrieve()
                .toEntity(TransactionGroupResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> settleTransactionGroup(UUID referenceId) {
        log.debug("Calling settleTransactionGroup: referenceId={}", referenceId);

        return webClient.post()
                .uri("/api/v1/ledger/groups/{referenceId}/settle", referenceId)
                .retrieve()
                .toEntity(TransactionGroupResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> releaseTransactionGroup(UUID referenceId, String reason) {
        log.debug("Calling releaseTransactionGroup: referenceId={}, reason={}", referenceId, reason);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/groups/{referenceId}/release")
                        .queryParam("reason", reason)
                        .build(referenceId))
                .retrieve()
                .toEntity(TransactionGroupResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> cancelTransactionGroup(UUID referenceId, String reason) {
        log.debug("Calling cancelTransactionGroup: referenceId={}, reason={}", referenceId, reason);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/groups/{referenceId}/cancel")
                        .queryParam("reason", reason)
                        .build(referenceId))
                .retrieve()
                .toEntity(TransactionGroupResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<TransferResponse> transfer(Integer senderId, Integer recipientId, Long amount) {
        log.debug("Calling transfer: senderId={}, recipientId={}, amount={}", senderId, recipientId, amount);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/transfer")
                        .queryParam("senderId", senderId)
                        .queryParam("recipientId", recipientId)
                        .queryParam("amount", amount)
                        .build())
                .retrieve()
                .toEntity(TransferResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<BalanceResponse> getBalance(Integer walletId) {
        log.debug("Calling getBalance: walletId={}", walletId);

        return webClient.get()
                .uri("/api/v1/ledger/wallets/{walletId}/balance", walletId)
                .retrieve()
                .toEntity(BalanceResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<GroupStatusResponse> getGroupStatus(UUID referenceId) {
        log.debug("Calling getGroupStatus: referenceId={}", referenceId);

        return webClient.get()
                .uri("/api/v1/ledger/groups/{referenceId}/status", referenceId)
                .retrieve()
                .toEntity(GroupStatusResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<List<TransactionDTO>> getGroupTransactions(UUID referenceId) {
        log.debug("Calling getGroupTransactions: referenceId={}", referenceId);

        return webClient.get()
                .uri("/api/v1/ledger/groups/{referenceId}/transactions", referenceId)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<TransactionDTO>>() {})
                .block();
    }

    // ==================== Settlement Operations ====================

    @Override
    public ResponseEntity<SettlementResponse> calculateSettlement(Long merchantId) {
        log.debug("Calling calculateSettlement: merchantId={}", merchantId);

        return webClient.get()
                .uri("/api/v1/ledger/settlement/merchants/{merchantId}/calculate", merchantId)
                .retrieve()
                .toEntity(SettlementResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<SettlementResponse> executeSettlement(Long merchantId) {
        log.debug("Calling executeSettlement: merchantId={}", merchantId);

        return webClient.post()
                .uri("/api/v1/ledger/settlement/merchants/{merchantId}/execute", merchantId)
                .retrieve()
                .toEntity(SettlementResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<SettlementResponse> getSettlement(UUID settlementId) {
        log.debug("Calling getSettlement: settlementId={}", settlementId);

        return webClient.get()
                .uri("/api/v1/ledger/settlement/{settlementId}", settlementId)
                .retrieve()
                .toEntity(SettlementResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<PagedResponse<SettlementHistoryDTO>> getSettlementHistory(
            Long merchantId, int page, int size) {
        log.debug("Calling getSettlementHistory: merchantId={}, page={}, size={}", merchantId, page, size);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/ledger/settlement/merchants/{merchantId}/history")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(merchantId))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PagedResponse<SettlementHistoryDTO>>() {})
                .block();
    }
}
