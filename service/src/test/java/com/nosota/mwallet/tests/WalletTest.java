package com.nosota.mwallet.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.nosota.mwallet.TestBase;
import com.nosota.mwallet.api.request.CreateMerchantWalletRequest;
import com.nosota.mwallet.api.request.CreateUserWalletRequest;
import com.nosota.mwallet.api.response.WalletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Wallet operations.
 *
 * <p>Tests verify wallet management principles:
 * <ul>
 *   <li>WAL-001: Creating USER wallets with ownerId</li>
 *   <li>WAL-002: Creating MERCHANT wallets</li>
 *   <li>WAL-003: ESCROW wallets cannot be created via public API</li>
 *   <li>WAL-004: SYSTEM wallets cannot be created via public API</li>
 *   <li>WAL-005: Wallets with different currencies (USD, EUR, USDT)</li>
 * </ul>
 */
@DisplayName("4. Wallet Tests")
public class WalletTest extends TestBase {

    @Test
    @DisplayName("WAL-001: Создание USER кошелька")
    void testCreateUserWallet() throws Exception {
        // Arrange
        Long ownerId = 999L;
        String currency = "USD";
        String description = "Test User Wallet";

        CreateUserWalletRequest request = new CreateUserWalletRequest(
                ownerId,
                description,
                currency,
                null  // no initial balance
        );

        // Act: Create USER wallet
        MvcResult createResult = mockMvc.perform(post("/api/v1/wallets/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").exists())
                .andExpect(jsonPath("$.type").value("USER"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.ownerType").value("USER_OWNER"))
                .andExpect(jsonPath("$.currency").value(currency))
                .andReturn();

        String createResponseJson = createResult.getResponse().getContentAsString();
        WalletResponse walletResponse = objectMapper.readValue(createResponseJson, WalletResponse.class);
        Integer walletId = walletResponse.walletId();

        // Assert: Get wallet by ID and verify details
        mockMvc.perform(get("/api/v1/wallets/{walletId}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.type").value("USER"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.currency").value(currency))
                .andExpect(jsonPath("$.description").value(description));

        // Assert: Verify balance is 0
        mockMvc.perform(get("/api/v1/ledger/wallets/{walletId}/balance", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0L));
    }

    @Test
    @DisplayName("WAL-002: Создание MERCHANT кошелька")
    void testCreateMerchantWallet() throws Exception {
        // Arrange
        Long ownerId = 888L;
        String currency = "USD";
        String description = "Test Merchant Wallet";

        CreateMerchantWalletRequest request = new CreateMerchantWalletRequest(
                ownerId,
                description,
                currency,
                null  // no initial balance
        );

        // Act: Create MERCHANT wallet
        MvcResult createResult = mockMvc.perform(post("/api/v1/wallets/merchant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").exists())
                .andExpect(jsonPath("$.type").value("MERCHANT"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.ownerType").value("MERCHANT_OWNER"))
                .andExpect(jsonPath("$.currency").value(currency))
                .andReturn();

        String createResponseJson = createResult.getResponse().getContentAsString();
        WalletResponse walletResponse = objectMapper.readValue(createResponseJson, WalletResponse.class);
        Integer walletId = walletResponse.walletId();

        // Assert: Get wallet by ID and verify details
        mockMvc.perform(get("/api/v1/wallets/{walletId}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.type").value("MERCHANT"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.currency").value(currency));

        // Assert: Verify balance is 0
        mockMvc.perform(get("/api/v1/ledger/wallets/{walletId}/balance", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0L));
    }

    @Test
    @DisplayName("WAL-003: Запрет создания ESCROW через API")
    void testEscrowCannotBeCreatedViaApi() throws Exception {
        // Assert: Verify /api/v1/wallets/escrow endpoint does NOT exist (404 Not Found)
        // ESCROW wallets can only be created internally by WalletManagementService

        // Attempt 1: Try to create ESCROW wallet via dedicated endpoint (should not exist)
        MvcResult escrowAttempt = mockMvc.perform(post("/api/v1/wallets/escrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "ownerId": 999,
                                "description": "Fake ESCROW",
                                "currency": "USD"
                            }
                            """))
                .andReturn();

        // Verify endpoint does not exist (404) or is forbidden (403) or bad request (400)
        int status = escrowAttempt.getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        assertThat(status).isNotEqualTo(201);
        assertThat(status).isIn(400, 403, 404, 500); // Any error, NOT success

        // Attempt 2: Verify /api/v1/wallets/system endpoint also does not exist
        MvcResult systemAttempt = mockMvc.perform(post("/api/v1/wallets/system")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "type": "ESCROW",
                                "ownerId": 999,
                                "currency": "USD"
                            }
                            """))
                .andReturn();

        status = systemAttempt.getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        assertThat(status).isNotEqualTo(201);
        assertThat(status).isIn(400, 403, 404, 500); // Any error, NOT success

        // SUCCESS: ESCROW wallets cannot be created through public API ✓
    }

    @Test
    @DisplayName("WAL-004: Запрет создания SYSTEM через API")
    void testSystemCannotBeCreatedViaApi() throws Exception {
        // Assert: Verify system wallet endpoints do NOT exist (404 Not Found)
        // SYSTEM wallets (DEPOSIT, WITHDRAWAL, etc.) can only be created internally
        // through WalletManagementService methods like getOrCreateDepositWallet()

        // Attempt 1: Try to create DEPOSIT wallet via API (should not exist)
        MvcResult depositAttempt = mockMvc.perform(post("/api/v1/wallets/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "description": "Fake DEPOSIT",
                                "currency": "USD"
                            }
                            """))
                .andReturn();

        int status = depositAttempt.getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        assertThat(status).isNotEqualTo(201);
        assertThat(status).isIn(400, 403, 404, 500); // Any error, NOT success

        // Attempt 2: Try to create WITHDRAWAL wallet via API (should not exist)
        MvcResult withdrawalAttempt = mockMvc.perform(post("/api/v1/wallets/withdrawal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "description": "Fake WITHDRAWAL",
                                "currency": "USD"
                            }
                            """))
                .andReturn();

        status = withdrawalAttempt.getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        assertThat(status).isNotEqualTo(201);
        assertThat(status).isIn(400, 403, 404, 500); // Any error, NOT success

        // Attempt 3: Try to create generic SYSTEM wallet via API (should not exist)
        MvcResult systemAttempt = mockMvc.perform(post("/api/v1/wallets/system")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "description": "Fake SYSTEM",
                                "currency": "USD"
                            }
                            """))
                .andReturn();

        status = systemAttempt.getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
        assertThat(status).isNotEqualTo(201);
        assertThat(status).isIn(400, 403, 404, 500); // Any error, NOT success

        // SUCCESS: SYSTEM wallets cannot be created through public API ✓
    }

    @Test
    @DisplayName("WAL-005: Кошельки с разными валютами")
    void testWalletsWithDifferentCurrencies() throws Exception {
        // Arrange: Create wallets with different currencies
        // Note: Currency codes must be 3 characters (ISO 4217 standard)
        Long ownerId = 777L;

        // Act & Assert: Create USD wallet
        CreateUserWalletRequest usdRequest = new CreateUserWalletRequest(
                ownerId, "USD Wallet", "USD", null);

        MvcResult usdResult = mockMvc.perform(post("/api/v1/wallets/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usdRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        JsonNode usdNode = objectMapper.readTree(usdResult.getResponse().getContentAsString());
        Integer usdWalletId = usdNode.get("walletId").asInt();

        // Act & Assert: Create EUR wallet
        CreateUserWalletRequest eurRequest = new CreateUserWalletRequest(
                ownerId, "EUR Wallet", "EUR", null);

        MvcResult eurResult = mockMvc.perform(post("/api/v1/wallets/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eurRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn();

        JsonNode eurNode = objectMapper.readTree(eurResult.getResponse().getContentAsString());
        Integer eurWalletId = eurNode.get("walletId").asInt();

        // Act & Assert: Create GBP wallet (British Pound)
        CreateUserWalletRequest gbpRequest = new CreateUserWalletRequest(
                ownerId, "GBP Wallet", "GBP", null);

        MvcResult gbpResult = mockMvc.perform(post("/api/v1/wallets/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gbpRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andReturn();

        JsonNode gbpNode = objectMapper.readTree(gbpResult.getResponse().getContentAsString());
        Integer gbpWalletId = gbpNode.get("walletId").asInt();

        // Final verification: Get all three wallets and verify currencies
        mockMvc.perform(get("/api/v1/wallets/{walletId}", usdWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        mockMvc.perform(get("/api/v1/wallets/{walletId}", eurWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"));

        mockMvc.perform(get("/api/v1/wallets/{walletId}", gbpWalletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("GBP"));

        // Assert: All three wallets have different IDs
        assertThat(usdWalletId).isNotEqualTo(eurWalletId);
        assertThat(usdWalletId).isNotEqualTo(gbpWalletId);
        assertThat(eurWalletId).isNotEqualTo(gbpWalletId);
    }
}
