package com.nosota.mwallet.api;

import com.nosota.mwallet.api.dto.PagedResponse;
import com.nosota.mwallet.api.dto.RefundHistoryDTO;
import com.nosota.mwallet.api.dto.SettlementHistoryDTO;
import com.nosota.mwallet.api.request.DepositRequest;
import com.nosota.mwallet.api.request.RefundRequest;
import com.nosota.mwallet.api.request.WithdrawalRequest;
import com.nosota.mwallet.api.response.DepositResponse;
import com.nosota.mwallet.api.response.RefundResponse;
import com.nosota.mwallet.api.response.SettlementResponse;
import com.nosota.mwallet.api.response.WithdrawalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;

/**
 * WebClient-based implementation of PaymentApi for consuming the payment service.
 *
 * <p>This client can be used by other services to interact with high-level payment operations.
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
 *     public PaymentClient paymentClient(WebClient mwalletWebClient) {
 *         return new PaymentClient(mwalletWebClient);
 *     }
 * }
 * }
 * </pre>
 */
@RequiredArgsConstructor
@Slf4j
public class PaymentClient implements PaymentApi {

    private final WebClient webClient;

    // ==================== Settlement Operations ====================

    @Override
    public ResponseEntity<SettlementResponse> calculateSettlement(Long merchantId) {
        log.debug("Calling calculateSettlement: merchantId={}", merchantId);

        return webClient.get()
                .uri("/api/v1/payment/settlement/merchants/{merchantId}/calculate", merchantId)
                .retrieve()
                .toEntity(SettlementResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<SettlementResponse> executeSettlement(Long merchantId) {
        log.debug("Calling executeSettlement: merchantId={}", merchantId);

        return webClient.post()
                .uri("/api/v1/payment/settlement/merchants/{merchantId}/execute", merchantId)
                .retrieve()
                .toEntity(SettlementResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<SettlementResponse> getSettlement(UUID settlementId) {
        log.debug("Calling getSettlement: settlementId={}", settlementId);

        return webClient.get()
                .uri("/api/v1/payment/settlement/{settlementId}", settlementId)
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
                        .path("/api/v1/payment/settlement/merchants/{merchantId}/history")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(merchantId))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PagedResponse<SettlementHistoryDTO>>() {})
                .block();
    }

    // ==================== Refund Operations ====================

    @Override
    public ResponseEntity<RefundResponse> createRefund(RefundRequest request) {
        log.debug("Calling createRefund: transactionGroupId={}, amount={}, initiator={}",
                request.transactionGroupId(), request.amount(), request.initiator());

        return webClient.post()
                .uri("/api/v1/payment/refund")
                .bodyValue(request)
                .retrieve()
                .toEntity(RefundResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<RefundResponse> getRefund(UUID refundId) {
        log.debug("Calling getRefund: refundId={}", refundId);

        return webClient.get()
                .uri("/api/v1/payment/refund/{refundId}", refundId)
                .retrieve()
                .toEntity(RefundResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<PagedResponse<RefundHistoryDTO>> getRefundHistory(
            Long merchantId, int page, int size) {
        log.debug("Calling getRefundHistory: merchantId={}, page={}, size={}", merchantId, page, size);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/payment/refund/merchants/{merchantId}/history")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(merchantId))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<PagedResponse<RefundHistoryDTO>>() {})
                .block();
    }

    @Override
    public ResponseEntity<List<RefundResponse>> getRefundsByOrder(UUID transactionGroupId) {
        log.debug("Calling getRefundsByOrder: transactionGroupId={}", transactionGroupId);

        return webClient.get()
                .uri("/api/v1/payment/refund/orders/{transactionGroupId}", transactionGroupId)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<RefundResponse>>() {})
                .block();
    }

    // ==================== Deposit/Withdrawal Operations ====================

    @Override
    public ResponseEntity<DepositResponse> deposit(DepositRequest request) throws Exception {
        log.debug("Calling deposit: walletId={}, amount={}, externalReference={}",
                request.walletId(), request.amount(), request.externalReference());

        return webClient.post()
                .uri("/api/v1/payment/deposit")
                .bodyValue(request)
                .retrieve()
                .toEntity(DepositResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<WithdrawalResponse> withdraw(WithdrawalRequest request) throws Exception {
        log.debug("Calling withdraw: walletId={}, amount={}, destinationAccount={}",
                request.walletId(), request.amount(), request.destinationAccount());

        return webClient.post()
                .uri("/api/v1/payment/withdrawal")
                .bodyValue(request)
                .retrieve()
                .toEntity(WithdrawalResponse.class)
                .block();
    }
}
