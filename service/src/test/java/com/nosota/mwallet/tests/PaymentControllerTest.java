package com.nosota.mwallet.tests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.api.model.RefundInitiator;
import com.nosota.mwallet.api.request.RefundRequest;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PaymentController via REST API with MockMvc.
 *
 * <p>Tests high-level payment operations:
 * <ul>
 *   <li>Settlement operations (calculate, execute, get, history)</li>
 *   <li>Refund operations (create, get, history, getByOrder)</li>
 * </ul>
 *
 * <p>All tests use MockMvc to test through the controller layer.
 */
public class PaymentControllerTest extends TestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    // Wallet IDs for tests
    private Integer escrowWalletId;
    private Integer merchantWalletId;
    private Integer systemWalletId;
    private Integer buyerWalletId;

    // Merchant and buyer owner IDs
    private Long merchantOwnerId;
    private Long buyerOwnerId;

    /**
     * Setup test data before each test.
     * Creates required wallets: ESCROW, MERCHANT, SYSTEM, and BUYER (USER).
     */
    @BeforeEach
    public void setupWallets() {
        // Create ESCROW wallet
        escrowWalletId = walletManagementService.createEscrowWallet("Test ESCROW");

        // Create MERCHANT wallet
        merchantWalletId = createMerchantWallet("Test MERCHANT");
        Wallet merchantWallet = walletRepository.findById(merchantWalletId).orElseThrow();
        merchantOwnerId = merchantWallet.getOwnerId();

        // Create SYSTEM wallet
        systemWalletId = walletManagementService.createSystemWallet("Test SYSTEM");

        // Create BUYER (USER) wallet with initial balance
        buyerWalletId = createUserWalletWithBalance("Test BUYER", 100000L); // 1000.00 in cents
        Wallet buyerWallet = walletRepository.findById(buyerWalletId).orElseThrow();
        buyerOwnerId = buyerWallet.getOwnerId();
    }

    // ==================== Settlement Tests ====================

    @Test
    public void calculateSettlement_WhenNoTransactions_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/payment/settlement/merchants/{merchantId}/calculate", merchantOwnerId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict()); // IllegalStateException → 409
    }

    @Test
    public void executeSettlement_WhenNoTransactions_ShouldFail() throws Exception {
        mockMvc.perform(post("/api/v1/payment/settlement/merchants/{merchantId}/execute", merchantOwnerId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict()); // IllegalStateException → 409
    }

    @Test
    public void getSettlement_WhenNotFound_ShouldReturn404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payment/settlement/{settlementId}", randomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getSettlementHistory_WhenNoData_ShouldReturnEmptyList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/payment/settlement/merchants/{merchantId}/history", merchantOwnerId)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseJson, new TypeReference<>() {});

        assertThat(response.get("totalRecords")).isEqualTo(0);
        assertThat(response.get("data")).asList().isEmpty();
    }

    // ==================== Refund Tests ====================

    @Test
    public void createRefund_WhenNoSettlement_ShouldFail() throws Exception {
        UUID randomTransactionGroupId = UUID.randomUUID();

        RefundRequest request = new RefundRequest(
                randomTransactionGroupId,
                10000L, // 100.00
                "Customer request",
                RefundInitiator.MERCHANT
        );

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/payment/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound()); // Settlement not found
    }

    @Test
    public void getRefund_WhenNotFound_ShouldReturn404() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/payment/refund/{refundId}", randomId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void getRefundHistory_WhenNoData_ShouldReturnEmptyList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/payment/refund/merchants/{merchantId}/history", merchantOwnerId)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseJson, new TypeReference<>() {});

        assertThat(response.get("totalRecords")).isEqualTo(0);
        assertThat(response.get("data")).asList().isEmpty();
    }

    @Test
    public void getRefundsByOrder_WhenNoRefunds_ShouldReturnEmptyList() throws Exception {
        UUID randomTransactionGroupId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(get("/api/v1/payment/refund/orders/{transactionGroupId}", randomTransactionGroupId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        assertThat(responseJson).isEqualTo("[]");
    }
}
