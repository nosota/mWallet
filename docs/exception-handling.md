# Exception Handling

## Overview

mWallet implements a centralized exception handling strategy using Spring's `@RestControllerAdvice` to provide consistent error responses across all REST endpoints.

## GlobalExceptionHandler

Location: `service/src/main/java/com/nosota/mwallet/exception/GlobalExceptionHandler.java`

The `GlobalExceptionHandler` intercepts exceptions thrown by controllers and maps them to appropriate HTTP status codes with standardized error responses.

## Exception Mapping

### Business Logic Exceptions → 400 Bad Request

Exceptions related to invalid operations or business rule violations:

```java
@ExceptionHandler(InsufficientFundsException.class)
public ResponseEntity<ErrorResponse> handleInsufficientFunds(
        InsufficientFundsException ex, HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.of(
        HttpStatus.BAD_REQUEST.value(),
        "Insufficient Funds",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

**Handled exceptions**:
- `InsufficientFundsException` - Wallet has insufficient balance
- `TransactionGroupZeroingOutException` - Transaction group doesn't balance
- `IllegalArgumentException` - Invalid input parameters

**HTTP Status**: `400 Bad Request`

### Not Found Exceptions → 404 Not Found

Exceptions when requested resources don't exist:

```java
@ExceptionHandler(EntityNotFoundException.class)
public ResponseEntity<ErrorResponse> handleEntityNotFound(
        EntityNotFoundException ex, HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.of(
        HttpStatus.NOT_FOUND.value(),
        "Entity Not Found",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
}
```

**Handled exceptions**:
- `EntityNotFoundException` (JPA) - Settlement or Refund not found
- `WalletNotFoundException` - Wallet doesn't exist
- `TransactionNotFoundException` - Transaction doesn't exist

**HTTP Status**: `404 Not Found`

### State Conflict Exceptions → 409 Conflict

Exceptions when operation conflicts with current state:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponse> handleIllegalState(
        IllegalStateException ex, HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.of(
        HttpStatus.CONFLICT.value(),
        "Invalid State",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

**Handled exceptions**:
- `IllegalStateException` - Invalid operation for current state
  - No unsettled transaction groups for settlement
  - Total amount below minimum threshold
  - Settlement already executed

**HTTP Status**: `409 Conflict`

### Generic Exceptions → 500 Internal Server Error

Unexpected exceptions that indicate system errors:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGenericException(
        Exception ex, HttpServletRequest request) {

    String correlationId = MDC.get("correlationId");
    log.error("Unexpected error [correlationId={}]", correlationId, ex);

    ErrorResponse error = ErrorResponse.of(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "Internal Server Error",
        "An unexpected error occurred. Please contact support with correlation ID: "
            + correlationId,
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
}
```

**HTTP Status**: `500 Internal Server Error`

**Note**: Generic message returned to client, full stack trace logged server-side

## Error Response Format

All error responses use a consistent JSON structure:

```java
public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path);
    }
}
```

### Example Error Response

```json
{
  "timestamp": "2025-12-29T18:30:45.123",
  "status": 400,
  "error": "Insufficient Funds",
  "message": "Insufficient funds in wallet 123: available=100, required=200",
  "path": "/api/v1/ledger/wallets/123/hold-debit"
}
```

## Correlation ID Logging

All exceptions are logged with a correlation ID for request tracing:

```java
String correlationId = MDC.get("correlationId");
log.error("Insufficient funds error [correlationId={}]: {}", correlationId, ex.getMessage());
```

**Benefits**:
- Trace requests across services
- Link client errors to server logs
- Debug production issues

**Note**: Correlation ID propagation requires middleware (not implemented in current version)

## Custom Exceptions

### Creating New Exceptions

1. **Extend appropriate base exception**:
```java
public class RefundNotAllowedException extends RuntimeException {
    public RefundNotAllowedException(String message) {
        super(message);
    }
}
```

2. **Add handler to GlobalExceptionHandler**:
```java
@ExceptionHandler(RefundNotAllowedException.class)
public ResponseEntity<ErrorResponse> handleRefundNotAllowed(
        RefundNotAllowedException ex, HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.of(
        HttpStatus.CONFLICT.value(),
        "Refund Not Allowed",
        ex.getMessage(),
        request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

### Exception Hierarchy

```
RuntimeException
├── InsufficientFundsException             → 400 Bad Request
├── TransactionGroupZeroingOutException    → 400 Bad Request
├── WalletNotFoundException                → 404 Not Found
├── TransactionNotFoundException           → 404 Not Found
├── IllegalArgumentException               → 400 Bad Request
└── IllegalStateException                  → 409 Conflict
```

**Design Decision**: All custom exceptions extend `RuntimeException` (unchecked) to avoid cluttering service method signatures with checked exception declarations.

## Exception Handling Best Practices

### 1. Use Specific Exceptions

```java
// Good - specific exception with context
throw new InsufficientFundsException(
    String.format("Insufficient funds in wallet %d: available=%d, required=%d",
        walletId, availableBalance, requiredAmount));

// Bad - generic exception without context
throw new RuntimeException("Not enough money");
```

### 2. Include Context in Messages

Provide enough information to diagnose the issue:
```java
// Good
"Settlement not found: cb3f7d15-8a2e-4f1a-9d3c-5e8f9a1b2c3d"

// Bad
"Not found"
```

### 3. Don't Expose Sensitive Data

```java
// Good
"Insufficient funds in wallet 123"

// Bad
"User John Doe (SSN: 123-45-6789) has insufficient funds"
```

### 4. Log Before Throwing

```java
public void processPayment(Long walletId, Long amount) {
    if (balance < amount) {
        log.warn("Payment failed: wallet={}, balance={}, amount={}",
            walletId, balance, amount);
        throw new InsufficientFundsException(...);
    }
}
```

### 5. Don't Catch and Rethrow Unnecessarily

```java
// Bad - unnecessary catch and rethrow
try {
    service.doSomething();
} catch (Exception e) {
    throw new RuntimeException(e);  // Adds no value
}

// Good - let exception propagate naturally
service.doSomething();
```

## Testing Exception Handling

### Test Error Responses

```java
@Test
public void holdDebit_WithInsufficientFunds_ShouldReturn400() throws Exception {
    Integer walletId = createUserWalletWithBalance("test", 100L);
    UUID groupId = createTransactionGroup();

    mockMvc.perform(
        post("/api/v1/ledger/wallets/{id}/hold-debit", walletId)
            .param("amount", "200")
            .param("referenceId", groupId.toString())
            .contentType(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isBadRequest())
    .andExpect(jsonPath("$.error").value("Insufficient Funds"))
    .andExpect(jsonPath("$.message").value(containsString("available=100")))
    .andExpect(jsonPath("$.message").value(containsString("required=200")));
}
```

### Test Not Found Scenarios

```java
@Test
public void getSettlement_WhenNotFound_ShouldReturn404() throws Exception {
    UUID randomId = UUID.randomUUID();

    mockMvc.perform(
        get("/api/v1/payment/settlement/{id}", randomId)
            .contentType(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isNotFound())
    .andExpect(jsonPath("$.error").value("Entity Not Found"))
    .andExpect(jsonPath("$.message").value(containsString(randomId.toString())));
}
```

## Exception Handling Flow

```
┌────────────────────────┐
│ Client makes request   │
└───────────┬────────────┘
            │
            ↓
┌────────────────────────┐
│ Controller receives    │
│ and validates request  │
└───────────┬────────────┘
            │
            ↓
┌────────────────────────┐
│ Service processes      │
│ business logic         │
└───────────┬────────────┘
            │
            ↓
    Exception thrown?
            │
     ┌──────┴──────┐
     │             │
    Yes           No
     │             │
     ↓             ↓
┌─────────────┐  ┌────────────────┐
│ Global      │  │ Return success │
│ Exception   │  │ response       │
│ Handler     │  └────────────────┘
│ intercepts  │
└─────┬───────┘
      │
      ↓
┌─────────────────────────┐
│ Map to HTTP status      │
│ Create ErrorResponse    │
│ Log with correlation ID │
└───────────┬─────────────┘
            │
            ↓
┌────────────────────────┐
│ Client receives error  │
│ with consistent format │
└────────────────────────┘
```

## HTTP Status Code Summary

| Status Code | Meaning                    | When to Use                                      |
|-------------|----------------------------|--------------------------------------------------|
| 400         | Bad Request                | Invalid input, business rule violations          |
| 404         | Not Found                  | Requested resource doesn't exist                 |
| 409         | Conflict                   | Operation conflicts with current state           |
| 500         | Internal Server Error      | Unexpected system errors                         |

## Related Documentation

- [Architecture Overview](./architecture.md)
- [API Design](./api-design.md)
- [Testing Guide](./testing.md)
