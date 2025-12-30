package com.nosota.mwallet.api;

import com.nosota.mwallet.api.request.CreateMerchantWalletRequest;
import com.nosota.mwallet.api.request.CreateUserWalletRequest;
import com.nosota.mwallet.api.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient-based implementation of WalletApi for consuming the mWallet wallet service.
 *
 * <p>This client provides access to wallet management operations:
 * creating USER/MERCHANT wallets and retrieving wallet information.
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
 *     public WalletClient walletClient(WebClient mwalletWebClient) {
 *         return new WalletClient(mwalletWebClient);
 *     }
 * }
 * }
 * </pre>
 */
@RequiredArgsConstructor
@Slf4j
public class WalletClient implements WalletApi {

    private final WebClient webClient;

    @Override
    public ResponseEntity<WalletResponse> createUserWallet(CreateUserWalletRequest request) {
        log.debug("Calling createUserWallet: ownerId={}, currency={}, initialBalance={}",
                request.ownerId(), request.currency(), request.initialBalance());

        return webClient.post()
                .uri("/api/v1/wallets/user")
                .bodyValue(request)
                .retrieve()
                .toEntity(WalletResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<WalletResponse> createMerchantWallet(CreateMerchantWalletRequest request) {
        log.debug("Calling createMerchantWallet: ownerId={}, currency={}, initialBalance={}",
                request.ownerId(), request.currency(), request.initialBalance());

        return webClient.post()
                .uri("/api/v1/wallets/merchant")
                .bodyValue(request)
                .retrieve()
                .toEntity(WalletResponse.class)
                .block();
    }

    @Override
    public ResponseEntity<WalletResponse> getWallet(Integer walletId) {
        log.debug("Calling getWallet: walletId={}", walletId);

        return webClient.get()
                .uri("/api/v1/wallets/{walletId}", walletId)
                .retrieve()
                .toEntity(WalletResponse.class)
                .block();
    }
}
