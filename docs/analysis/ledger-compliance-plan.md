# План Приведения к Ledger-Стандартам

**Версия**: 1.0
**Дата**: 27 декабря 2025
**Статус**: 📋 Draft — требуется утверждение
**Приоритет**: 🔴 Высокий

---

## Содержание

1. [Обзор](#обзор)
2. [Стратегия Миграции](#стратегия-миграции)
3. [Фаза 1: Терминология и Статусы](#фаза-1-терминология-и-статусы)
4. [Фаза 2: Settlement Механизм](#фаза-2-settlement-механизм)
5. [Фаза 3: Ledger Service](#фаза-3-ledger-service)
6. [Фаза 4: Batch Processing](#фаза-4-batch-processing)
7. [Фаза 5: Документация и Финализация](#фаза-5-документация-и-финализация)
8. [Риски и Митигация](#риски-и-митигация)
9. [Чеклист](#чеклист)

---

## Обзор

### Цель

Привести проект mWallet к **стандартам банковского ledger** для использования как фундамента платёжной платформы.

### Scope

- ✅ Терминология (статусы, операции)
- ✅ Settlement механизм
- ✅ Разделение Release vs Cancel
- ✅ Ledger Service архитектура
- ✅ Batch processing
- ✅ Обновление документации

### Вне Scope

- ❌ Изменение трёхуровневой архитектуры хранения (работает хорошо)
- ❌ Изменение double-entry механизма (соответствует требованиям)
- ❌ Миграция на другую СУБД

### Оценка Трудозатрат

| Фаза | Трудозатраты | Риск | Приоритет |
|------|--------------|------|-----------|
| Фаза 1: Терминология | 40 ч | Средний | 🔴 Критично |
| Фаза 2: Settlement | 60 ч | Высокий | 🔴 Критично |
| Фаза 3: Ledger Service | 80 ч | Средний | 🟡 Высокий |
| Фаза 4: Batch Processing | 100 ч | Высокий | 🟡 Высокий |
| Фаза 5: Документация | 20 ч | Низкий | 🟢 Средний |
| **ИТОГО** | **300 ч** | - | - |

**Срок**: 2-3 месяца при одном full-time разработчике

---

## Стратегия Миграции

### Подход: Поэтапная Миграция без Downtime

**Стратегия**: Добавление новой функциональности параллельно старой с постепенным переключением.

```
Шаг 1: Добавить новые статусы (параллельно старым)
Шаг 2: Реализовать новые методы (параллельно старым)
Шаг 3: Мигрировать клиентов на новые методы
Шаг 4: Пометить старые методы @Deprecated
Шаг 5: Удалить старые методы (через N месяцев)
```

### Принципы

1. **Обратная совместимость** — старый API продолжает работать
2. **Feature Flags** — возможность откатить изменения
3. **Постепенная миграция** — поэтапное переключение клиентов
4. **Comprehensive Testing** — полное покрытие тестами
5. **Documentation First** — документация перед кодом

---

## Фаза 1: Терминология и Статусы

**Цель**: Привести терминологию к ledger-стандартам

**Трудозатраты**: 40 часов

**Риск**: Средний (изменение core enum, требует тщательного тестирования)

### 1.1. Новые Статусы

#### Шаг 1.1.1: Расширить TransactionStatus enum

**Текущий код**:
```java
public enum TransactionStatus {
    HOLD,      // Удержание
    RESERVE,   // Резервирование (неправильная терминология!)
    CONFIRMED, // Подтверждено (слишком общее)
    REJECTED   // Отклонено (технический термин)
}
```

**Новый код** (с обратной совместимостью):
```java
public enum TransactionStatus {
    // === Стандартные ledger-статусы ===
    HOLD,           // Удержание средств (для дебета и кредита)
    RELEASED,       // Разблокировано (после выполнения условий)
    CANCELLED,      // Отменено (до выполнения условий)
    SETTLED,        // Финально переведено (после release)

    // === Deprecated (для обратной совместимости) ===
    @Deprecated(since = "2.0.0", forRemoval = true)
    RESERVE,        // Используйте HOLD вместо RESERVE

    @Deprecated(since = "2.0.0", forRemoval = true)
    CONFIRMED,      // Используйте RELEASED или SETTLED

    @Deprecated(since = "2.0.0", forRemoval = true)
    REJECTED;       // Используйте CANCELLED

    /**
     * Маппинг старых статусов на новые
     */
    public TransactionStatus toNewStatus() {
        return switch (this) {
            case RESERVE -> HOLD;
            case CONFIRMED -> RELEASED;  // или SETTLED (зависит от контекста)
            case REJECTED -> CANCELLED;
            default -> this;
        };
    }

    /**
     * Проверка, является ли статус финальным
     */
    public boolean isFinal() {
        return this == SETTLED || this == CANCELLED;
    }

    /**
     * Проверка, является ли статус временным
     */
    public boolean isTemporary() {
        return this == HOLD || this == RELEASED;
    }
}
```

**Файлы для изменения**:
- `service/src/main/java/com/nosota/mwallet/model/TransactionStatus.java`

**Миграция базы данных**:
```sql
-- V2.01__add_new_transaction_statuses.sql

-- Добавить новые значения (если используется enum type в PostgreSQL)
-- Если используется VARCHAR, изменения не требуются

-- Для безопасной миграции, сначала проверим текущие значения
SELECT DISTINCT status FROM transaction;
SELECT DISTINCT status FROM transaction_snapshot;
SELECT DISTINCT status FROM transaction_snapshot_archive;

-- Комментарий: новые статусы будут использоваться постепенно
-- Старые статусы остаются для обратной совместимости
```

---

#### Шаг 1.1.2: Добавить маппинг старых статусов

Создать utility класс для маппинга:

```java
/**
 * Утилита для маппинга между старыми и новыми статусами
 *
 * @since 2.0.0
 */
@Component
public class TransactionStatusMapper {

    /**
     * Преобразует старый статус в новый
     */
    public TransactionStatus mapToNewStatus(TransactionStatus oldStatus, TransactionType type) {
        return switch (oldStatus) {
            case RESERVE -> TransactionStatus.HOLD;
            case CONFIRMED -> {
                // Контекстный маппинг: CONFIRMED может быть RELEASED или SETTLED
                // Нужна дополнительная логика для определения
                yield TransactionStatus.RELEASED; // Временно, требуется уточнение
            }
            case REJECTED -> TransactionStatus.CANCELLED;
            case HOLD, RELEASED, CANCELLED, SETTLED -> oldStatus;
        };
    }

    /**
     * Преобразует новый статус в старый (для обратной совместимости)
     */
    public TransactionStatus mapToLegacyStatus(TransactionStatus newStatus) {
        return switch (newStatus) {
            case RELEASED, SETTLED -> TransactionStatus.CONFIRMED;
            case CANCELLED -> TransactionStatus.REJECTED;
            case HOLD -> TransactionStatus.HOLD;
            default -> newStatus;
        };
    }

    /**
     * Проверяет, требуется ли миграция статуса
     */
    public boolean needsMigration(TransactionStatus status) {
        return status == TransactionStatus.RESERVE
            || status == TransactionStatus.CONFIRMED
            || status == TransactionStatus.REJECTED;
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/util/TransactionStatusMapper.java`

---

### 1.2. Обновить Сервисы

#### Шаг 1.2.1: WalletService — добавить новые методы

**Новые методы** (параллельно старым):

```java
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletService {

    // === НОВЫЕ МЕТОДЫ (ledger-стандарты) ===

    /**
     * Удерживает средства для дебета (списание).
     * Заменяет метод hold() с явной семантикой.
     *
     * @since 2.0.0
     */
    @Transactional
    public Integer holdDebit(@NotNull Integer walletId,
                             @Positive Long amount,
                             @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {

        // Проверка баланса
        Long availableBalance = walletBalanceService.getAvailableBalance(walletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException(
                "Insufficient funds in wallet " + walletId
            );
        }

        return createTransaction(
            walletId,
            -amount,
            TransactionType.DEBIT,
            TransactionStatus.HOLD,
            referenceId
        );
    }

    /**
     * Удерживает средства для кредита (зачисление).
     * Заменяет метод reserve() с правильной терминологией.
     *
     * @since 2.0.0
     */
    @Transactional
    public Integer holdCredit(@NotNull Integer walletId,
                              @Positive Long amount,
                              @NotNull UUID referenceId)
            throws WalletNotFoundException {

        // Для кредита проверка баланса не нужна (входящие средства)
        return createTransaction(
            walletId,
            amount,
            TransactionType.CREDIT,
            TransactionStatus.HOLD,
            referenceId
        );
    }

    /**
     * Разблокирует удержанные средства после выполнения условий.
     * Заменяет метод confirm() с явной семантикой.
     *
     * @since 2.0.0
     */
    @Transactional
    public Integer release(@NotNull Integer walletId,
                           @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        Transaction heldTransaction = findHeldTransaction(walletId, referenceId);

        return createTransaction(
            walletId,
            heldTransaction.getAmount(),
            heldTransaction.getType(),
            TransactionStatus.RELEASED,
            referenceId
        );
    }

    /**
     * Отменяет удержанные средства до выполнения условий.
     * Заменяет метод reject() с явной семантикой.
     *
     * @since 2.0.0
     */
    @Transactional
    public Integer cancel(@NotNull Integer walletId,
                          @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        Transaction heldTransaction = findHeldTransaction(walletId, referenceId);

        return createTransaction(
            walletId,
            heldTransaction.getAmount(),
            heldTransaction.getType(),
            TransactionStatus.CANCELLED,
            referenceId
        );
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private Transaction findHeldTransaction(Integer walletId, UUID referenceId)
            throws TransactionNotFoundException {
        return transactionRepository
            .findByWalletIdAndReferenceIdAndStatuses(
                walletId,
                referenceId,
                TransactionStatus.HOLD
            )
            .orElseThrow(() -> new TransactionNotFoundException(
                "No held transaction found for wallet " + walletId +
                " and reference " + referenceId
            ));
    }

    private Integer createTransaction(Integer walletId,
                                     Long amount,
                                     TransactionType type,
                                     TransactionStatus status,
                                     UUID referenceId) {
        Transaction transaction = new Transaction();
        transaction.setWalletId(walletId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setReferenceId(referenceId);

        if (status == TransactionStatus.HOLD) {
            transaction.setHoldReserveTimestamp(LocalDateTime.now());
        } else {
            transaction.setConfirmRejectTimestamp(LocalDateTime.now());
        }

        Transaction saved = transactionRepository.save(transaction);
        return saved.getId();
    }

    // === СТАРЫЕ МЕТОДЫ (@Deprecated) ===

    /**
     * @deprecated Используйте {@link #holdDebit(Integer, Long, UUID)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @Transactional
    public Integer hold(@NotNull Integer walletId,
                        @Positive Long amount,
                        @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {
        log.warn("Deprecated method hold() called. Use holdDebit() instead.");
        return holdDebit(walletId, amount, referenceId);
    }

    /**
     * @deprecated Используйте {@link #holdCredit(Integer, Long, UUID)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @Transactional
    public Integer reserve(@NotNull Integer walletId,
                           @Positive Long amount,
                           @NotNull UUID referenceId)
            throws WalletNotFoundException {
        log.warn("Deprecated method reserve() called. Use holdCredit() instead.");
        return holdCredit(walletId, amount, referenceId);
    }

    /**
     * @deprecated Используйте {@link #release(Integer, UUID)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @Transactional
    public Integer confirm(@NotNull Integer walletId,
                           @NotNull UUID referenceId)
            throws TransactionNotFoundException {
        log.warn("Deprecated method confirm() called. Use release() instead.");
        return release(walletId, referenceId);
    }

    /**
     * @deprecated Используйте {@link #cancel(Integer, UUID)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @Transactional
    public Integer reject(@NotNull Integer walletId,
                          @NotNull UUID referenceId)
            throws TransactionNotFoundException {
        log.warn("Deprecated method reject() called. Use cancel() instead.");
        return cancel(walletId, referenceId);
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/WalletService.java`

---

#### Шаг 1.2.2: TransactionService — обновить методы

```java
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class TransactionService {

    // === НОВЫЕ МЕТОДЫ ===

    /**
     * Разблокирует все транзакции в группе
     *
     * @since 2.0.0
     */
    @Transactional
    public void releaseTransactionGroup(@NotNull UUID referenceId)
            throws TransactionNotFoundException, TransactionGroupZeroingOutException {

        TransactionGroup group = getTransactionGroup(referenceId);

        // Zero-sum проверка
        validateZeroSum(referenceId);

        // Release всех транзакций
        List<Transaction> transactions =
            transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);

        for (Transaction tx : transactions) {
            walletService.release(tx.getWalletId(), referenceId);
        }

        group.setStatus(TransactionGroupStatus.RELEASED);
        transactionGroupRepository.save(group);
    }

    /**
     * Отменяет все транзакции в группе
     *
     * @since 2.0.0
     */
    @Transactional
    public void cancelTransactionGroup(@NotNull UUID referenceId,
                                       @NotEmpty String reason)
            throws TransactionNotFoundException {

        TransactionGroup group = getTransactionGroup(referenceId);

        // Cancel всех транзакций
        List<Transaction> transactions =
            transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);

        for (Transaction tx : transactions) {
            walletService.cancel(tx.getWalletId(), referenceId);
        }

        group.setStatus(TransactionGroupStatus.CANCELLED);
        group.setReason(reason);
        transactionGroupRepository.save(group);
    }

    // === DEPRECATED МЕТОДЫ ===

    /**
     * @deprecated Используйте {@link #releaseTransactionGroup(UUID)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @Transactional
    public void confirmTransactionGroup(@NotNull UUID referenceId)
            throws TransactionNotFoundException, TransactionGroupZeroingOutException {
        log.warn("Deprecated method confirmTransactionGroup() called. " +
                 "Use releaseTransactionGroup() instead.");
        releaseTransactionGroup(referenceId);
    }

    /**
     * @deprecated Используйте {@link #cancelTransactionGroup(UUID, String)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @Transactional
    public void rejectTransactionGroup(@NotNull UUID referenceId,
                                       @NotEmpty String reason)
            throws TransactionNotFoundException {
        log.warn("Deprecated method rejectTransactionGroup() called. " +
                 "Use cancelTransactionGroup() instead.");
        cancelTransactionGroup(referenceId, reason);
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private TransactionGroup getTransactionGroup(UUID referenceId) {
        return transactionGroupRepository.findById(referenceId)
            .orElseThrow(() -> new EntityNotFoundException(
                "No transaction group found: " + referenceId
            ));
    }

    private void validateZeroSum(UUID referenceId)
            throws TransactionGroupZeroingOutException {
        Long reconciliationAmount =
            transactionRepository.getReconciliationAmountByGroupId(referenceId);

        if (reconciliationAmount != 0) {
            throw new TransactionGroupZeroingOutException(
                "Transaction group " + referenceId + " is not reconciled. " +
                "Sum: " + reconciliationAmount
            );
        }
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/TransactionService.java`

---

### 1.3. Тестирование

#### Шаг 1.3.1: Unit Tests

```java
@SpringBootTest
class WalletServiceTest extends TestBase {

    @Test
    void testHoldDebit_Success() {
        // Given
        Integer walletId = createWalletWithBalance(10000L);
        UUID groupId = UUID.randomUUID();

        // When
        Integer txId = walletService.holdDebit(walletId, 1000L, groupId);

        // Then
        assertThat(txId).isNotNull();
        Long balance = walletBalanceService.getAvailableBalance(walletId);
        assertThat(balance).isEqualTo(9000L); // 10000 - 1000
    }

    @Test
    void testHoldCredit_Success() {
        // Given
        Integer walletId = createWallet();
        UUID groupId = UUID.randomUUID();

        // When
        Integer txId = walletService.holdCredit(walletId, 1000L, groupId);

        // Then
        assertThat(txId).isNotNull();
        // Средства ещё не доступны (только HOLD)
        Long balance = walletBalanceService.getAvailableBalance(walletId);
        assertThat(balance).isEqualTo(0L);
    }

    @Test
    void testRelease_Success() {
        // Given
        Integer walletId = createWalletWithBalance(10000L);
        UUID groupId = UUID.randomUUID();
        walletService.holdDebit(walletId, 1000L, groupId);

        // When
        Integer txId = walletService.release(walletId, groupId);

        // Then
        assertThat(txId).isNotNull();

        Transaction tx = transactionRepository.findById(txId).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.RELEASED);
    }

    @Test
    void testCancel_Success() {
        // Given
        Integer walletId = createWalletWithBalance(10000L);
        UUID groupId = UUID.randomUUID();
        walletService.holdDebit(walletId, 1000L, groupId);

        // When
        walletService.cancel(walletId, groupId);

        // Then
        Long balance = walletBalanceService.getAvailableBalance(walletId);
        assertThat(balance).isEqualTo(10000L); // Средства вернулись
    }

    @Test
    void testBackwardCompatibility_HoldMethod() {
        // Given
        Integer walletId = createWalletWithBalance(10000L);
        UUID groupId = UUID.randomUUID();

        // When (используем deprecated метод)
        @SuppressWarnings("deprecation")
        Integer txId = walletService.hold(walletId, 1000L, groupId);

        // Then (должен работать так же)
        assertThat(txId).isNotNull();
        Long balance = walletBalanceService.getAvailableBalance(walletId);
        assertThat(balance).isEqualTo(9000L);
    }
}
```

**Файл**: `service/src/test/java/com/nosota/mwallet/tests/WalletServiceTerminologyTest.java`

---

### 1.4. Deliverables Фазы 1

- ✅ Новые статусы в `TransactionStatus` enum
- ✅ Mapper для старых/новых статусов
- ✅ Новые методы в `WalletService` (holdDebit, holdCredit, release, cancel)
- ✅ Новые методы в `TransactionService` (releaseTransactionGroup, cancelTransactionGroup)
- ✅ @Deprecated аннотации на старых методах
- ✅ Unit tests для новых методов
- ✅ Backward compatibility tests
- ✅ Миграция базы данных (если нужна)

---

## Фаза 2: Settlement Механизм

**Цель**: Добавить финальный перевод средств (settlement)

**Трудозатраты**: 60 часов

**Риск**: Высокий (изменение core бизнес-логики)

### 2.1. Концепция Settlement

**Жизненный цикл транзакции**:
```
HOLD → RELEASED → SETTLED
  ↓        ↓         ↓
Удержание → Разблокировано → Финально переведено
```

**Отличия**:
- **RELEASED**: Средства разблокированы, но ещё не переведены окончательно
- **SETTLED**: Финальный перевод выполнен, операция необратима

### 2.2. Новые Методы

#### Шаг 2.2.1: WalletService.settle()

```java
/**
 * Финальный перевод средств после release.
 * Операция необратима.
 *
 * @since 2.0.0
 */
@Transactional
public Integer settle(@NotNull Integer walletId,
                      @NotNull UUID referenceId)
        throws TransactionNotFoundException {

    // Найти RELEASED транзакцию
    Optional<Transaction> releasedTx = transactionRepository
        .findByWalletIdAndReferenceIdAndStatuses(
            walletId,
            referenceId,
            TransactionStatus.RELEASED
        );

    if (!releasedTx.isPresent()) {
        throw new TransactionNotFoundException(
            "No released transaction found for wallet " + walletId
        );
    }

    Transaction released = releasedTx.get();

    // Создать SETTLED транзакцию
    Transaction settlement = new Transaction();
    settlement.setWalletId(walletId);
    settlement.setAmount(released.getAmount());
    settlement.setType(released.getType());
    settlement.setStatus(TransactionStatus.SETTLED);
    settlement.setReferenceId(referenceId);
    settlement.setConfirmRejectTimestamp(LocalDateTime.now());
    settlement.setDescription("Settlement for transaction " + released.getId());

    Transaction saved = transactionRepository.save(settlement);

    log.info("Settled transaction {} for wallet {}",
             saved.getId(), walletId);

    return saved.getId();
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/WalletService.java`

---

#### Шаг 2.2.2: TransactionService.settleTransactionGroup()

```java
/**
 * Финальный перевод всех транзакций в группе.
 * Группа должна быть в статусе RELEASED.
 *
 * @since 2.0.0
 */
@Transactional
public void settleTransactionGroup(@NotNull UUID referenceId)
        throws TransactionNotFoundException {

    TransactionGroup group = getTransactionGroup(referenceId);

    // Проверка: группа должна быть RELEASED
    if (group.getStatus() != TransactionGroupStatus.RELEASED) {
        throw new IllegalStateException(
            "Cannot settle group " + referenceId +
            " in status " + group.getStatus() +
            ". Must be RELEASED first."
        );
    }

    // Settle всех транзакций
    List<Transaction> transactions =
        transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);

    for (Transaction tx : transactions) {
        walletService.settle(tx.getWalletId(), referenceId);
    }

    group.setStatus(TransactionGroupStatus.SETTLED);
    transactionGroupRepository.save(group);

    log.info("Settled transaction group {}", referenceId);
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/TransactionService.java`

---

### 2.3. Обновить TransactionGroupStatus

```java
public enum TransactionGroupStatus {
    IN_PROGRESS,  // Создаётся
    RELEASED,     // Разблокировано (NEW!)
    SETTLED,      // Финально переведено (NEW!)
    CANCELLED,    // Отменено (переименовано из REJECTED)

    @Deprecated(since = "2.0.0", forRemoval = true)
    CONFIRMED,    // Используйте RELEASED или SETTLED

    @Deprecated(since = "2.0.0", forRemoval = true)
    REJECTED;     // Используйте CANCELLED
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/model/TransactionGroupStatus.java`

---

### 2.4. Новый Workflow

```java
/**
 * Пример: Перевод с немедленным settlement
 */
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public UUID transferWithImmediateSettlement(Integer senderId,
                                            Integer recipientId,
                                            Long amount) throws Exception {
    UUID groupId = createTransactionGroup();

    try {
        // 1. Hold средства
        walletService.holdDebit(senderId, amount, groupId);
        walletService.holdCredit(recipientId, amount, groupId);

        // 2. Release (разблокировка)
        releaseTransactionGroup(groupId);

        // 3. Settlement (финальный перевод)
        settleTransactionGroup(groupId);

    } catch (Exception e) {
        cancelTransactionGroup(groupId, e.getMessage());
        throw e;
    }

    return groupId;
}

/**
 * Пример: Escrow с отложенным settlement
 */
@Transactional(Transactional.TxType.NOT_SUPPORTED)
public UUID escrowTransfer(Integer buyerId,
                          Integer escrowWalletId,
                          Long amount) throws Exception {
    UUID groupId = createTransactionGroup();

    try {
        // 1. Hold средства в escrow
        walletService.holdDebit(buyerId, amount, groupId);
        walletService.holdCredit(escrowWalletId, amount, groupId);

        // 2. Release в escrow (продавец отгрузил товар)
        releaseTransactionGroup(groupId);

        // 3. Settlement откладывается до подтверждения покупателя!
        // Вызовется позже: settleTransactionGroup(groupId)

    } catch (Exception e) {
        cancelTransactionGroup(groupId, e.getMessage());
        throw e;
    }

    return groupId;
}
```

---

### 2.5. Миграция Базы Данных

```sql
-- V2.02__add_settlement_status.sql

-- Добавить комментарии для новых статусов
COMMENT ON COLUMN transaction_group.status IS
'Group status: IN_PROGRESS, RELEASED, SETTLED, CANCELLED (CONFIRMED and REJECTED deprecated)';

-- Создать индекс для быстрого поиска групп для settlement
CREATE INDEX idx_transaction_group_status_released
ON transaction_group(status)
WHERE status = 'RELEASED';

-- Функция для валидации статусов
CREATE OR REPLACE FUNCTION validate_transaction_group_status_transition()
RETURNS TRIGGER AS $$
BEGIN
    -- Валидация переходов состояний
    IF OLD.status = 'SETTLED' AND NEW.status != 'SETTLED' THEN
        RAISE EXCEPTION 'Cannot change status from SETTLED';
    END IF;

    IF OLD.status = 'CANCELLED' AND NEW.status != 'CANCELLED' THEN
        RAISE EXCEPTION 'Cannot change status from CANCELLED';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Триггер для валидации
CREATE TRIGGER validate_status_transition
BEFORE UPDATE ON transaction_group
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION validate_transaction_group_status_transition();
```

---

### 2.6. Deliverables Фазы 2

- ✅ Метод `WalletService.settle()`
- ✅ Метод `TransactionService.settleTransactionGroup()`
- ✅ Обновлённый `TransactionGroupStatus` enum
- ✅ Миграция базы данных
- ✅ Unit tests для settlement
- ✅ Integration tests для escrow workflow
- ✅ Документация workflow (HOLD → RELEASED → SETTLED)

---

## Фаза 3: Ledger Service

**Цель**: Выделить Ledger Service для централизованного управления

**Трудозатраты**: 80 часов

**Риск**: Средний (рефакторинг архитектуры)

### 3.1. Новая Архитектура

```
До рефакторинга:
WalletService: hold, reserve, confirm, reject
TransactionService: createGroup, confirmGroup, rejectGroup

После рефакторинга:
LedgerService: holdFunds, releaseFunds, cancelHold, settleFunds
TransactionGroupService: createGroup, releaseGroup, cancelGroup, settleGroup
WalletService: createWallet, getBalance (без ledger-логики)
```

### 3.2. Создать LedgerService

```java
/**
 * Сервис управления Ledger (журналом финансовых операций).
 * Centralizes all ledger operations.
 *
 * @since 2.0.0
 */
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class LedgerService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;

    /**
     * Удерживает средства (debit).
     */
    @Transactional
    public LedgerEntry holdDebit(@NotNull Integer walletId,
                                 @Positive Long amount,
                                 @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {

        validateWalletExists(walletId);
        validateSufficientBalance(walletId, amount);

        return createLedgerEntry(
            walletId,
            -amount,
            TransactionType.DEBIT,
            TransactionStatus.HOLD,
            referenceId,
            "Hold debit: " + amount + " cents"
        );
    }

    /**
     * Удерживает средства (credit).
     */
    @Transactional
    public LedgerEntry holdCredit(@NotNull Integer walletId,
                                  @Positive Long amount,
                                  @NotNull UUID referenceId)
            throws WalletNotFoundException {

        validateWalletExists(walletId);

        return createLedgerEntry(
            walletId,
            amount,
            TransactionType.CREDIT,
            TransactionStatus.HOLD,
            referenceId,
            "Hold credit: " + amount + " cents"
        );
    }

    /**
     * Разблокирует удержанные средства.
     */
    @Transactional
    public LedgerEntry releaseFunds(@NotNull Integer walletId,
                                    @NotNull UUID referenceId)
            throws LedgerEntryNotFoundException {

        Transaction held = findHeldTransaction(walletId, referenceId);

        return createLedgerEntry(
            walletId,
            held.getAmount(),
            held.getType(),
            TransactionStatus.RELEASED,
            referenceId,
            "Release funds for transaction " + held.getId()
        );
    }

    /**
     * Отменяет удержание средств.
     */
    @Transactional
    public LedgerEntry cancelHold(@NotNull Integer walletId,
                                  @NotNull UUID referenceId)
            throws LedgerEntryNotFoundException {

        Transaction held = findHeldTransaction(walletId, referenceId);

        return createLedgerEntry(
            walletId,
            held.getAmount(),
            held.getType(),
            TransactionStatus.CANCELLED,
            referenceId,
            "Cancel hold for transaction " + held.getId()
        );
    }

    /**
     * Финальный перевод средств.
     */
    @Transactional
    public LedgerEntry settleFunds(@NotNull Integer walletId,
                                   @NotNull UUID referenceId)
            throws LedgerEntryNotFoundException {

        Transaction released = findReleasedTransaction(walletId, referenceId);

        return createLedgerEntry(
            walletId,
            released.getAmount(),
            released.getType(),
            TransactionStatus.SETTLED,
            referenceId,
            "Settlement for transaction " + released.getId()
        );
    }

    /**
     * Получить все ledger entries для кошелька.
     */
    public List<LedgerEntry> getLedgerEntries(@NotNull Integer walletId) {
        List<Transaction> transactions =
            transactionRepository.findAllByWalletId(walletId);

        return transactions.stream()
            .map(this::toLedgerEntry)
            .collect(Collectors.toList());
    }

    /**
     * Получить ledger entries для группы транзакций.
     */
    public List<LedgerEntry> getLedgerEntriesByGroup(@NotNull UUID referenceId) {
        List<Transaction> transactions =
            transactionRepository.findByReferenceId(referenceId);

        return transactions.stream()
            .map(this::toLedgerEntry)
            .collect(Collectors.toList());
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private LedgerEntry createLedgerEntry(Integer walletId,
                                          Long amount,
                                          TransactionType type,
                                          TransactionStatus status,
                                          UUID referenceId,
                                          String description) {
        Transaction transaction = new Transaction();
        transaction.setWalletId(walletId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(status);
        transaction.setReferenceId(referenceId);
        transaction.setDescription(description);

        if (status == TransactionStatus.HOLD) {
            transaction.setHoldReserveTimestamp(LocalDateTime.now());
        } else {
            transaction.setConfirmRejectTimestamp(LocalDateTime.now());
        }

        Transaction saved = transactionRepository.save(transaction);

        log.info("Created ledger entry: wallet={}, amount={}, type={}, status={}",
                 walletId, amount, type, status);

        return toLedgerEntry(saved);
    }

    private Transaction findHeldTransaction(Integer walletId, UUID referenceId)
            throws LedgerEntryNotFoundException {
        return transactionRepository
            .findByWalletIdAndReferenceIdAndStatuses(
                walletId,
                referenceId,
                TransactionStatus.HOLD
            )
            .orElseThrow(() -> new LedgerEntryNotFoundException(
                "No held transaction found for wallet " + walletId
            ));
    }

    private Transaction findReleasedTransaction(Integer walletId, UUID referenceId)
            throws LedgerEntryNotFoundException {
        return transactionRepository
            .findByWalletIdAndReferenceIdAndStatuses(
                walletId,
                referenceId,
                TransactionStatus.RELEASED
            )
            .orElseThrow(() -> new LedgerEntryNotFoundException(
                "No released transaction found for wallet " + walletId
            ));
    }

    private void validateWalletExists(Integer walletId)
            throws WalletNotFoundException {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(
                "Wallet not found: " + walletId
            );
        }
    }

    private void validateSufficientBalance(Integer walletId, Long amount)
            throws InsufficientFundsException {
        Long available = walletBalanceService.getAvailableBalance(walletId);
        if (available < amount) {
            throw new InsufficientFundsException(
                "Insufficient funds in wallet " + walletId +
                ": available=" + available + ", required=" + amount
            );
        }
    }

    private LedgerEntry toLedgerEntry(Transaction transaction) {
        return LedgerEntry.builder()
            .id(transaction.getId())
            .walletId(transaction.getWalletId())
            .amount(transaction.getAmount())
            .type(transaction.getType())
            .status(transaction.getStatus())
            .referenceId(transaction.getReferenceId())
            .timestamp(transaction.getConfirmRejectTimestamp() != null
                ? transaction.getConfirmRejectTimestamp()
                : transaction.getHoldReserveTimestamp())
            .description(transaction.getDescription())
            .build();
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/LedgerService.java`

---

### 3.3. LedgerEntry DTO

```java
/**
 * Ledger Entry DTO - представление записи в ledger.
 *
 * @since 2.0.0
 */
@Data
@Builder
public class LedgerEntry {
    private Integer id;
    private Integer walletId;
    private Long amount;
    private TransactionType type;
    private TransactionStatus status;
    private UUID referenceId;
    private LocalDateTime timestamp;
    private String description;

    /**
     * Является ли запись дебетом (списание)
     */
    public boolean isDebit() {
        return type == TransactionType.DEBIT;
    }

    /**
     * Является ли запись кредитом (зачисление)
     */
    public boolean isCredit() {
        return type == TransactionType.CREDIT;
    }

    /**
     * Финальная запись (settled или cancelled)
     */
    public boolean isFinal() {
        return status == TransactionStatus.SETTLED
            || status == TransactionStatus.CANCELLED;
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/dto/LedgerEntry.java`

---

### 3.4. TransactionGroupService

```java
/**
 * Сервис управления группами транзакций.
 * Uses LedgerService for all ledger operations.
 *
 * @since 2.0.0
 */
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class TransactionGroupService {

    private final TransactionGroupRepository transactionGroupRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    @Transactional
    public UUID createGroup() {
        TransactionGroup group = new TransactionGroup();
        group.setStatus(TransactionGroupStatus.IN_PROGRESS);
        group = transactionGroupRepository.save(group);
        return group.getId();
    }

    @Transactional
    public void releaseGroup(@NotNull UUID groupId)
            throws TransactionGroupZeroingOutException {

        TransactionGroup group = getGroup(groupId);
        validateZeroSum(groupId);

        List<Transaction> transactions =
            transactionRepository.findByReferenceIdOrderByIdDesc(groupId);

        for (Transaction tx : transactions) {
            ledgerService.releaseFunds(tx.getWalletId(), groupId);
        }

        group.setStatus(TransactionGroupStatus.RELEASED);
        transactionGroupRepository.save(group);
    }

    @Transactional
    public void cancelGroup(@NotNull UUID groupId, @NotEmpty String reason) {
        TransactionGroup group = getGroup(groupId);

        List<Transaction> transactions =
            transactionRepository.findByReferenceIdOrderByIdDesc(groupId);

        for (Transaction tx : transactions) {
            ledgerService.cancelHold(tx.getWalletId(), groupId);
        }

        group.setStatus(TransactionGroupStatus.CANCELLED);
        group.setReason(reason);
        transactionGroupRepository.save(group);
    }

    @Transactional
    public void settleGroup(@NotNull UUID groupId) {
        TransactionGroup group = getGroup(groupId);

        if (group.getStatus() != TransactionGroupStatus.RELEASED) {
            throw new IllegalStateException(
                "Cannot settle group in status " + group.getStatus()
            );
        }

        List<Transaction> transactions =
            transactionRepository.findByReferenceIdOrderByIdDesc(groupId);

        for (Transaction tx : transactions) {
            ledgerService.settleFunds(tx.getWalletId(), groupId);
        }

        group.setStatus(TransactionGroupStatus.SETTLED);
        transactionGroupRepository.save(group);
    }

    private TransactionGroup getGroup(UUID groupId) {
        return transactionGroupRepository.findById(groupId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Transaction group not found: " + groupId
            ));
    }

    private void validateZeroSum(UUID groupId)
            throws TransactionGroupZeroingOutException {
        Long sum = transactionRepository.getReconciliationAmountByGroupId(groupId);
        if (sum != 0) {
            throw new TransactionGroupZeroingOutException(
                "Group " + groupId + " doesn't balance to zero: sum=" + sum
            );
        }
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/TransactionGroupService.java`

---

### 3.5. Обновить WalletService

Удалить ledger-логику из WalletService, делегировать в LedgerService:

```java
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletService {

    private final LedgerService ledgerService;

    /**
     * @deprecated Use {@link LedgerService#holdDebit(Integer, Long, UUID)}
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public Integer holdDebit(Integer walletId, Long amount, UUID referenceId) {
        return ledgerService.holdDebit(walletId, amount, referenceId).getId();
    }

    // Остальные методы удалены - используйте LedgerService
}
```

---

### 3.6. Deliverables Фазы 3

- ✅ `LedgerService` с всеми операциями
- ✅ `LedgerEntry` DTO
- ✅ `TransactionGroupService` (использует LedgerService)
- ✅ Рефакторинг `WalletService` (делегирование в LedgerService)
- ✅ Unit tests для LedgerService
- ✅ Integration tests для всей цепочки
- ✅ Миграция существующих тестов

---

## Фаза 4: Batch Processing

**Цель**: Добавить batch settlement для массовых операций

**Трудозатраты**: 100 часов

**Риск**: Высокий (асинхронная обработка, производительность)

### 4.1. SettlementBatchService

```java
/**
 * Сервис пакетного settlement.
 * Обрабатывает группы транзакций пакетами.
 *
 * @since 2.0.0
 */
@Service
@Slf4j
@AllArgsConstructor
public class SettlementBatchService {

    private final TransactionGroupRepository transactionGroupRepository;
    private final TransactionGroupService transactionGroupService;

    /**
     * Выполняет batch settlement для всех RELEASED групп.
     */
    @Scheduled(cron = "${mwallet.settlement.batch.cron:0 0 3 * * *}") // 3 AM
    public void scheduledBatchSettlement() {
        log.info("Starting scheduled batch settlement");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        BatchSettlementResult result = settleBatch(
            TransactionGroupStatus.RELEASED,
            cutoff
        );

        log.info("Batch settlement completed: {}", result);
    }

    /**
     * Выполняет batch settlement для групп в указанном статусе.
     */
    @Transactional
    public BatchSettlementResult settleBatch(
            @NotNull TransactionGroupStatus status,
            @NotNull LocalDateTime olderThan) {

        // Найти группы для settlement
        List<TransactionGroup> groups = transactionGroupRepository
            .findByStatusAndCreatedAtBefore(status, olderThan);

        int successCount = 0;
        int errorCount = 0;
        List<UUID> failed = new ArrayList<>();

        for (TransactionGroup group : groups) {
            try {
                transactionGroupService.settleGroup(group.getId());
                successCount++;
            } catch (Exception e) {
                errorCount++;
                failed.add(group.getId());
                log.error("Failed to settle group {}: {}",
                         group.getId(), e.getMessage(), e);
            }
        }

        return BatchSettlementResult.builder()
            .timestamp(LocalDateTime.now())
            .totalGroups(groups.size())
            .successCount(successCount)
            .errorCount(errorCount)
            .failedGroups(failed)
            .build();
    }

    /**
     * Отменяет все старые IN_PROGRESS группы (cleanup).
     */
    @Scheduled(cron = "${mwallet.cleanup.stale.cron:0 0 4 * * *}") // 4 AM
    public void cleanupStaleGroups() {
        log.info("Starting cleanup of stale transaction groups");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(7); // 7 дней

        List<TransactionGroup> staleGroups = transactionGroupRepository
            .findByStatusAndCreatedAtBefore(
                TransactionGroupStatus.IN_PROGRESS,
                cutoff
            );

        for (TransactionGroup group : staleGroups) {
            try {
                transactionGroupService.cancelGroup(
                    group.getId(),
                    "Cancelled: stale group (> 7 days old)"
                );
                log.warn("Cancelled stale group: {}", group.getId());
            } catch (Exception e) {
                log.error("Failed to cancel stale group {}: {}",
                         group.getId(), e.getMessage(), e);
            }
        }
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/service/SettlementBatchService.java`

---

### 4.2. BatchSettlementResult

```java
@Data
@Builder
public class BatchSettlementResult {
    private LocalDateTime timestamp;
    private int totalGroups;
    private int successCount;
    private int errorCount;
    private List<UUID> failedGroups;

    public double getSuccessRate() {
        return totalGroups > 0
            ? (double) successCount / totalGroups * 100
            : 0;
    }
}
```

**Файл**: `service/src/main/java/com/nosota/mwallet/dto/BatchSettlementResult.java`

---

### 4.3. Конфигурация

```yaml
# application.yaml
mwallet:
  settlement:
    batch:
      enabled: true
      cron: "0 0 3 * * *"  # 3 AM daily
      page-size: 1000      # Process 1000 groups at a time
  cleanup:
    stale:
      enabled: true
      cron: "0 0 4 * * *"  # 4 AM daily
      threshold-days: 7    # Groups older than 7 days
```

---

### 4.4. Расширить Repository

```java
public interface TransactionGroupRepository extends JpaRepository<TransactionGroup, UUID> {

    /**
     * Найти группы по статусу, созданные до указанной даты
     */
    List<TransactionGroup> findByStatusAndCreatedAtBefore(
        TransactionGroupStatus status,
        LocalDateTime before
    );

    /**
     * Посчитать группы в статусе
     */
    long countByStatus(TransactionGroupStatus status);
}
```

---

### 4.5. Deliverables Фазы 4

- ✅ `SettlementBatchService` с scheduled jobs
- ✅ `BatchSettlementResult` DTO
- ✅ Конфигурация batch processing
- ✅ Cleanup job для stale groups
- ✅ Расширенный `TransactionGroupRepository`
- ✅ Performance tests (обработка 10K+ групп)
- ✅ Monitoring и алерты

---

## Фаза 5: Документация и Финализация

**Цель**: Обновить всю документацию

**Трудозатраты**: 20 часов

**Риск**: Низкий

### 5.1. Обновить Документы

- ✅ `docs/architecture/transaction-lifecycle.md` — новый workflow
- ✅ `docs/api/services.md` — LedgerService API
- ✅ `docs/operations/settlement.md` — batch settlement operations
- ✅ `RELEASES.md` — release notes для v2.0.0
- ✅ `CHANGELOG.md` — детальный changelog

### 5.2. Создать Migration Guide

```markdown
# Migration Guide: v1.0.5 → v2.0.0

## Breaking Changes

### Terminology Changes

| Old | New | Migration |
|-----|-----|-----------|
| `RESERVE` status | `HOLD` | Use `holdCredit()` |
| `CONFIRMED` status | `RELEASED` or `SETTLED` | Context-dependent |
| `REJECTED` status | `CANCELLED` | Use `cancel()` |
| `WalletService.hold()` | `LedgerService.holdDebit()` | Direct replacement |
| `WalletService.reserve()` | `LedgerService.holdCredit()` | Direct replacement |
| `WalletService.confirm()` | `LedgerService.releaseFunds()` | Direct replacement |
| `WalletService.reject()` | `LedgerService.cancelHold()` | Direct replacement |

### Code Migration Examples

#### Before (v1.0.5)
```java
walletService.hold(senderId, amount, groupId);
walletService.reserve(recipientId, amount, groupId);
transactionService.confirmTransactionGroup(groupId);
```

#### After (v2.0.0)
```java
ledgerService.holdDebit(senderId, amount, groupId);
ledgerService.holdCredit(recipientId, amount, groupId);
transactionGroupService.releaseGroup(groupId);
transactionGroupService.settleGroup(groupId);
```
```

---

## Риски и Митигация

### Риск 1: Breaking Changes для Клиентов

**Вероятность**: Высокая
**Влияние**: Критическое

**Митигация**:
1. ✅ Сохранить старые методы с @Deprecated
2. ✅ Feature flags для постепенного переключения
3. ✅ Migration guide для клиентов
4. ✅ Длительный deprecation period (6-12 месяцев)

### Риск 2: Производительность Batch Processing

**Вероятность**: Средняя
**Влияние**: Высокое

**Митигация**:
1. ✅ Performance tests перед релизом
2. ✅ Pagination для больших batch
3. ✅ Мониторинг времени выполнения
4. ✅ Возможность отключить batch processing

### Риск 3: Data Corruption при Миграции

**Вероятность**: Низкая
**Влияние**: Критическое

**Митигация**:
1. ✅ Comprehensive tests перед миграцией
2. ✅ Backup базы данных
3. ✅ Rollback plan
4. ✅ Проверка reconciliation после миграции

### Риск 4: Недостаточное Тестирование

**Вероятность**: Средняя
**Влияние**: Высокое

**Митигация**:
1. ✅ Unit tests (coverage > 90%)
2. ✅ Integration tests
3. ✅ End-to-end tests
4. ✅ Load testing
5. ✅ Staging environment тестирование

---

## Чеклист

### Pre-Implementation

- [ ] Согласовать план с командой
- [ ] Создать feature branch: `feature/ledger-compliance`
- [ ] Настроить feature flags
- [ ] Создать test environment

### Фаза 1: Терминология

- [ ] Расширить `TransactionStatus` enum
- [ ] Создать `TransactionStatusMapper`
- [ ] Добавить новые методы в `WalletService`
- [ ] @Deprecated старые методы
- [ ] Unit tests (coverage > 90%)
- [ ] Integration tests
- [ ] Code review
- [ ] Merge в develop

### Фаза 2: Settlement

- [ ] Добавить `settle()` методы
- [ ] Обновить `TransactionGroupStatus`
- [ ] Миграция базы данных
- [ ] Unit tests для settlement
- [ ] Integration tests для workflow
- [ ] Code review
- [ ] Merge в develop

### Фаза 3: Ledger Service

- [ ] Создать `LedgerService`
- [ ] Создать `LedgerEntry` DTO
- [ ] Создать `TransactionGroupService`
- [ ] Рефакторинг `WalletService`
- [ ] Unit tests
- [ ] Integration tests
- [ ] Performance tests
- [ ] Code review
- [ ] Merge в develop

### Фаза 4: Batch Processing

- [ ] Создать `SettlementBatchService`
- [ ] Scheduled jobs конфигурация
- [ ] Performance tests (10K+ groups)
- [ ] Monitoring и алерты
- [ ] Code review
- [ ] Merge в develop

### Фаза 5: Документация

- [ ] Обновить architecture docs
- [ ] Обновить API docs
- [ ] Создать migration guide
- [ ] Обновить RELEASES.md
- [ ] Review документации

### Pre-Release

- [ ] Full regression testing
- [ ] Load testing
- [ ] Security review
- [ ] Staging deployment
- [ ] Beta testing с клиентами
- [ ] Final code review

### Release

- [ ] Tag версии v2.0.0
- [ ] Merge в main
- [ ] Production deployment
- [ ] Monitoring
- [ ] Communication с клиентами

---

## Контакты и Вопросы

Если есть вопросы по плану:
1. Создайте issue в репозитории
2. Отметьте team lead
3. Используйте метку `ledger-compliance`

**Дата следующего ревью плана**: TBD

---

**Конец плана**
