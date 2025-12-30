# Тестовый план финансовой платформы mWallet

## Версия документа

| Версия | Дата | Автор | Описание |
|--------|------|-------|----------|
| 1.0 | 2025-01-15 | Expert Review | Первичный тестовый план |

---

## Содержание

1. [Общая информация](#1-общая-информация)
2. [Тестовое окружение](#2-тестовое-окружение)
3. [Ledger Tests](#3-ledger-tests)
4. [Wallet Tests](#4-wallet-tests)
5. [Payment Tests](#5-payment-tests)
6. [Escrow Tests](#6-escrow-tests)
7. [Balance Tests](#7-balance-tests)
8. [Settlement Tests](#8-settlement-tests)
9. [Refund Tests](#9-refund-tests)
10. [Multi-Party Tests](#10-multi-party-tests)
11. [Edge Cases](#11-edge-cases)
12. [Negative Scenarios](#12-negative-scenarios)
13. [Idempotency Tests](#13-idempotency-tests)
14. [Hold Expiration Tests](#14-hold-expiration-tests-future)

---

## 1. Общая информация

### 1.1 Цель тестирования

Проверка корректности работы финансового ядра платформы mWallet, включая:
- Целостность ledger (immutability, double-entry)
- Корректность операций с кошельками
- Правильность расчёта балансов
- Корректность escrow-механизма
- Правильность settlement и refund

### 1.2 Принципы тестирования

- **Zero-Sum**: Сумма всех транзакций в системе всегда равна 0
- **Double-Entry**: Каждая операция создаёт минимум 2 транзакции (debit + credit)
- **Immutability**: Транзакции не редактируются, только создаются новые
- **Atomicity**: Группа транзакций выполняется целиком или не выполняется

### 1.3 Статусы транзакций

| Статус | Описание |
|--------|----------|
| HOLD | Средства заблокированы, ожидают финализации |
| SETTLED | Операция успешно завершена |
| RELEASED | Средства возвращены после диспута |
| CANCELLED | Операция отменена до исполнения |
| REFUNDED | Средства возвращены после settlement |

### 1.4 Комиссия

- Ставка: **3%** (0.03)
- Округление: **HALF_UP**
- При refund: комиссия остаётся у платформы

---

## 2. Тестовое окружение

### 2.1 Системные кошельки

| ID | Тип | Название | Начальный баланс |
|----|-----|----------|------------------|
| 1 | ESCROW | ESCROW_MAIN | 0 |
| 2 | SYSTEM | SYSTEM_FEE | 0 |
| 3 | SYSTEM | DEPOSIT | 0 |
| 4 | SYSTEM | WITHDRAWAL | 0 |

### 2.2 Тестовые пользователи

| ID | Тип | Название | Валюта | Начальный баланс |
|----|-----|----------|--------|------------------|
| 101 | USER | BUYER_1 | USD | 100000 |
| 102 | USER | BUYER_2 | USD | 50000 |
| 103 | USER | BUYER_EUR | EUR | 100000 |
| 201 | MERCHANT | MERCHANT_1 | USD | 0 |
| 202 | MERCHANT | MERCHANT_2 | USD | 0 |
| 203 | MERCHANT | MERCHANT_EUR | EUR | 0 |

### 2.3 Валюты

| Код | Описание | Minor Units |
|-----|----------|-------------|
| USD | US Dollar | cents (1/100) |
| EUR | Euro | cents (1/100) |
| USDT | Tether | cents (1/100) |

### 2.4 Формат сумм

Все суммы указаны в **minor units** (центы). Например:
- 10000 = $100.00
- 100 = $1.00
- 1 = $0.01

---

## 3. Ledger Tests

### 3.1 LED-001: Создание транзакции с double-entry

**Цель:** Проверить что каждая операция создаёт пару debit/credit с zero-sum

**Предусловия:**
- BUYER_1 balance: 100000
- MERCHANT_1 balance: 0

**Шаги:**

| Шаг | Операция | BUYER_1 | MERCHANT_1 | ESCROW | SYSTEM | DEPOSIT | WITHDRAWAL | Zero-Sum |
|-----|----------|---------|------------|--------|--------|---------|------------|----------|
| 0 | Initial state | 100000 | 0 | 0 | 0 | -100000 | 0 | ✓ (0) |
| 1 | Transfer 10000: BUYER_1 → MERCHANT_1 | 90000 | 10000 | 0 | 0 | -100000 | 0 | ✓ (0) |

**Проверки:**
- [ ] Создано 2 транзакции в одной группе
- [ ] Transaction 1: wallet=BUYER_1, amount=-10000, type=DEBIT
- [ ] Transaction 2: wallet=MERCHANT_1, amount=+10000, type=CREDIT
- [ ] SUM(amount) по группе = 0
- [ ] TransactionGroup.status = SETTLED

---

### 3.2 LED-002: Immutability — запрет изменения транзакций

**Цель:** Проверить что существующие транзакции нельзя изменить

**Предусловия:**
- Существует транзакция TX_1 с amount=10000

**Шаги:**

| Шаг | Операция | Ожидаемый результат |
|-----|----------|---------------------|
| 1 | UPDATE transaction SET amount=5000 WHERE id=TX_1 | Ошибка / Запрет |
| 2 | DELETE FROM transaction WHERE id=TX_1 | Ошибка / Запрет |
| 3 | UPDATE transaction SET status='CANCELLED' WHERE id=TX_1 | Ошибка / Запрет |

**Проверки:**
- [ ] Операции UPDATE/DELETE запрещены на уровне приложения
- [ ] Транзакция TX_1 не изменилась

---

### 3.3 LED-003: Reversal вместо удаления

**Цель:** Проверить что отмена операции создаёт новые транзакции, а не удаляет старые

**Предусловия:**
- BUYER_1 balance: 100000
- Создан HOLD на 10000

**Шаги:**

| Шаг | Операция | BUYER_1 total | BUYER_1 available | Транзакций в группе |
|-----|----------|---------------|-------------------|---------------------|
| 0 | Initial | 100000 | 100000 | 0 |
| 1 | Hold 10000 | 100000 | 90000 | 2 (HOLD) |
| 2 | Cancel hold | 100000 | 100000 | 4 (HOLD + CANCELLED) |

**Проверки:**
- [ ] Исходные HOLD транзакции сохранены (не удалены)
- [ ] Созданы новые CANCELLED транзакции
- [ ] Всего 4 транзакции в группе
- [ ] SUM(amount) по группе = 0

---

### 3.4 LED-004: Zero-Sum Reconciliation всей системы

**Цель:** Проверить что сумма всех транзакций в системе = 0

**Предусловия:**
- Выполнено несколько операций: deposits, payments, settlements, refunds

**Шаги:**

| Шаг | Операция | System Total |
|-----|----------|--------------|
| 1 | SELECT SUM(amount) FROM transaction | 0 |
| 2 | SELECT SUM(amount) FROM transaction WHERE status='SETTLED' | 0 |
| 3 | SELECT SUM(amount) FROM transaction WHERE status='HOLD' | 0 |

**Проверки:**
- [ ] Общая сумма всех транзакций = 0
- [ ] Сумма по каждому статусу = 0
- [ ] Сумма по каждой группе = 0

---

## 4. Wallet Tests

### 4.1 WAL-001: Создание USER кошелька

**Цель:** Проверить создание пользовательского кошелька

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Create wallet(type=USER, currency=USD, ownerId=999) | Wallet created |
| 2 | Get wallet by id | type=USER, currency=USD, balance=0 |

**Проверки:**
- [ ] Кошелёк создан с уникальным ID
- [ ] type = USER
- [ ] currency = USD
- [ ] balance = 0
- [ ] ownerId = 999

---

### 4.2 WAL-002: Создание MERCHANT кошелька

**Цель:** Проверить создание кошелька мерчанта

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Create wallet(type=MERCHANT, currency=USD, ownerId=888) | Wallet created |
| 2 | Get wallet by id | type=MERCHANT, currency=USD, balance=0 |

**Проверки:**
- [ ] Кошелёк создан
- [ ] type = MERCHANT
- [ ] ownerId = 888

---

### 4.3 WAL-003: Запрет создания ESCROW через API

**Цель:** Проверить что ESCROW кошельки нельзя создать через публичный API

**Шаги:**

| Шаг | Операция | Ожидаемый результат |
|-----|----------|---------------------|
| 1 | Create wallet(type=ESCROW) через public API | Error: Forbidden |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: 403 или аналогичный
- [ ] ESCROW кошелёк не создан

---

### 4.4 WAL-004: Запрет создания SYSTEM через API

**Цель:** Проверить что SYSTEM кошельки нельзя создать через публичный API

**Шаги:**

| Шаг | Операция | Ожидаемый результат |
|-----|----------|---------------------|
| 1 | Create wallet(type=SYSTEM) через public API | Error: Forbidden |

**Проверки:**
- [ ] Операция отклонена
- [ ] SYSTEM кошелёк не создан

---

### 4.5 WAL-005: Кошельки с разными валютами

**Цель:** Проверить создание кошельков в разных валютах

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Create wallet(currency=USD) | Wallet USD created |
| 2 | Create wallet(currency=EUR) | Wallet EUR created |
| 3 | Create wallet(currency=USDT) | Wallet USDT created |

**Проверки:**
- [ ] Созданы 3 кошелька с разными валютами
- [ ] Каждый имеет корректную валюту

---

## 5. Payment Tests

### 5.1 PAY-001: Простой перевод между кошельками

**Цель:** Проверить прямой перевод без escrow

**Предусловия:**
- BUYER_1 balance: 100000
- MERCHANT_1 balance: 0

**Шаги:**

| Шаг | Операция | BUYER_1 | MERCHANT_1 | ESCROW | SYSTEM | DEPOSIT | WITHDRAWAL | Zero-Sum |
|-----|----------|---------|------------|--------|--------|---------|------------|----------|
| 0 | Initial | 100000 | 0 | 0 | 0 | -100000 | 0 | ✓ |
| 1 | Transfer 25000: BUYER_1 → MERCHANT_1 | 75000 | 25000 | 0 | 0 | -100000 | 0 | ✓ |

**Проверки:**
- [ ] BUYER_1.balance = 75000
- [ ] MERCHANT_1.balance = 25000
- [ ] TransactionGroup.status = SETTLED
- [ ] Zero-sum сохранён

---

### 5.2 PAY-002: Hold → Settlement

**Цель:** Проверить двухфазный платёж

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | BUYER_1 total | BUYER_1 avail | MERCHANT_1 | ESCROW | Zero-Sum |
|-----|----------|---------------|---------------|------------|--------|----------|
| 0 | Initial | 100000 | 100000 | 0 | 0 | ✓ |
| 1 | Hold 20000: BUYER_1 → ESCROW | 100000 | 80000 | 0 | 0 | ✓ |
| 2 | Settle: ESCROW → MERCHANT_1 | 80000 | 80000 | 20000 | 0 | ✓ |

**Проверки:**
- [ ] После Hold: availableBalance уменьшился, totalBalance не изменился
- [ ] После Settle: деньги у MERCHANT_1
- [ ] TransactionGroup.status = SETTLED

---

### 5.3 PAY-003: Hold → Cancel

**Цель:** Проверить отмену hold до исполнения

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | BUYER_1 total | BUYER_1 avail | ESCROW | Status |
|-----|----------|---------------|---------------|--------|--------|
| 0 | Initial | 100000 | 100000 | 0 | - |
| 1 | Hold 15000 | 100000 | 85000 | 0 | HOLD |
| 2 | Cancel | 100000 | 100000 | 0 | CANCELLED |

**Проверки:**
- [ ] После Cancel: баланс полностью восстановлен
- [ ] TransactionGroup.status = CANCELLED
- [ ] Созданы CANCELLED транзакции (reversal)

---

### 5.4 PAY-004: Hold → Release

**Цель:** Проверить release после диспута

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | BUYER_1 total | BUYER_1 avail | ESCROW | Status |
|-----|----------|---------------|---------------|--------|--------|
| 0 | Initial | 100000 | 100000 | 0 | - |
| 1 | Hold 15000 | 100000 | 85000 | 0 | HOLD |
| 2 | Release (dispute resolved) | 100000 | 100000 | 0 | RELEASED |

**Проверки:**
- [ ] После Release: баланс восстановлен
- [ ] TransactionGroup.status = RELEASED
- [ ] Созданы RELEASED транзакции

---

### 5.5 PAY-005: Deposit — пополнение кошелька

**Цель:** Проверить пополнение кошелька из внешнего источника

**Предусловия:**
- NEW_USER balance: 0
- DEPOSIT balance: 0

**Шаги:**

| Шаг | Операция | NEW_USER | DEPOSIT | Zero-Sum |
|-----|----------|----------|---------|----------|
| 0 | Initial | 0 | 0 | ✓ |
| 1 | Deposit 50000 to NEW_USER | 50000 | -50000 | ✓ |

**Проверки:**
- [ ] NEW_USER.balance = 50000
- [ ] DEPOSIT.balance = -50000 (отрицательный — источник средств)
- [ ] Zero-sum сохранён
- [ ] TransactionGroup.status = SETTLED

---

### 5.6 PAY-006: Withdrawal — вывод средств

**Цель:** Проверить вывод средств из системы

**Предусловия:**
- MERCHANT_1 balance: 30000
- WITHDRAWAL balance: 0

**Шаги:**

| Шаг | Операция | MERCHANT_1 | WITHDRAWAL | Zero-Sum |
|-----|----------|------------|------------|----------|
| 0 | Initial | 30000 | 0 | ✓ |
| 1 | Withdraw 20000 from MERCHANT_1 | 10000 | 20000 | ✓ |

**Проверки:**
- [ ] MERCHANT_1.balance = 10000
- [ ] WITHDRAWAL.balance = 20000 (положительный — средства покинули систему)
- [ ] Zero-sum сохранён

---

## 6. Escrow Tests

### 6.1 ESC-001: Полный цикл Escrow (Buyer → Escrow → Merchant)

**Цель:** Проверить полный жизненный цикл escrow без комиссии settlement

**Предусловия:**
- BUYER_1 balance: 100000
- MERCHANT_1 balance: 0
- ESCROW balance: 0

**Шаги:**

| Шаг | Операция | BUYER_1 total | BUYER_1 avail | ESCROW | MERCHANT_1 | Zero-Sum |
|-----|----------|---------------|---------------|--------|------------|----------|
| 0 | Initial | 100000 | 100000 | 0 | 0 | ✓ |
| 1 | Hold 10000: BUYER → ESCROW | 100000 | 90000 | 0 | 0 | ✓ |
| 2 | Settle BUYER → ESCROW | 90000 | 90000 | 10000 | 0 | ✓ |
| 3 | Hold 10000: ESCROW → MERCHANT | 90000 | 90000 | 10000 | 0 | ✓ |
| 4 | Settle ESCROW → MERCHANT | 90000 | 90000 | 0 | 10000 | ✓ |

**Проверки:**
- [ ] Деньги прошли через ESCROW
- [ ] Финальные балансы корректны
- [ ] Zero-sum на каждом шаге

---

### 6.2 ESC-002: Escrow с отменой (Cancel)

**Цель:** Проверить отмену escrow до settlement

**Предусловия:**
- BUYER_1 balance: 100000
- ESCROW balance: 0

**Шаги:**

| Шаг | Операция | BUYER_1 total | BUYER_1 avail | ESCROW | Status |
|-----|----------|---------------|---------------|--------|--------|
| 0 | Initial | 100000 | 100000 | 0 | - |
| 1 | Hold 10000: BUYER → ESCROW | 100000 | 90000 | 0 | HOLD |
| 2 | Settle BUYER → ESCROW | 90000 | 90000 | 10000 | SETTLED |
| 3 | Cancel: return to BUYER | 100000 | 100000 | 0 | CANCELLED |

**Проверки:**
- [ ] Деньги вернулись BUYER_1
- [ ] ESCROW.balance = 0
- [ ] Созданы CANCELLED транзакции

---

### 6.3 ESC-003: Escrow с Release (диспут)

**Цель:** Проверить release средств после диспута

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | BUYER_1 | ESCROW | MERCHANT_1 | Status |
|-----|----------|---------|--------|------------|--------|
| 0 | Initial | 100000 | 0 | 0 | - |
| 1 | Hold + Settle to ESCROW | 90000 | 10000 | 0 | SETTLED |
| 2 | Dispute opened | 90000 | 10000 | 0 | DISPUTED |
| 3 | Release to BUYER | 100000 | 0 | 0 | RELEASED |

**Проверки:**
- [ ] После диспута деньги вернулись BUYER_1
- [ ] ESCROW.balance = 0
- [ ] MERCHANT_1 ничего не получил

---

## 7. Balance Tests

### 7.1 BAL-001: Total Balance vs Available Balance

**Цель:** Проверить разницу между total и available balance

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | Total | Available | Held |
|-----|----------|-------|-----------|------|
| 0 | Initial | 100000 | 100000 | 0 |
| 1 | Hold 30000 | 100000 | 70000 | 30000 |
| 2 | Hold 20000 (ещё один) | 100000 | 50000 | 50000 |
| 3 | Settle первый hold | 70000 | 50000 | 20000 |
| 4 | Cancel второй hold | 70000 | 70000 | 0 |

**Проверки:**
- [ ] totalBalance = SUM(SETTLED, RELEASED, CANCELLED, REFUNDED)
- [ ] heldAmount = SUM(HOLD where amount < 0)
- [ ] availableBalance = totalBalance - heldAmount

---

### 7.2 BAL-002: Balance после Refund

**Цель:** Проверить что REFUNDED транзакции учитываются в балансе

**Предусловия:**
- MERCHANT_1 balance: 10000

**Шаги:**

| Шаг | Операция | MERCHANT_1 total | BUYER_1 total |
|-----|----------|------------------|---------------|
| 0 | Initial | 10000 | 90000 |
| 1 | Refund 5000: MERCHANT → BUYER | 5000 | 95000 |

**Проверки:**
- [ ] MERCHANT_1.totalBalance уменьшился на 5000
- [ ] BUYER_1.totalBalance увеличился на 5000
- [ ] Транзакции со статусом REFUNDED учтены

---

### 7.3 BAL-003: Несколько Hold на одном кошельке

**Цель:** Проверить корректность расчёта при множественных hold

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | Total | Available | Held | Hold Count |
|-----|----------|-------|-----------|------|------------|
| 0 | Initial | 100000 | 100000 | 0 | 0 |
| 1 | Hold #1: 10000 | 100000 | 90000 | 10000 | 1 |
| 2 | Hold #2: 20000 | 100000 | 70000 | 30000 | 2 |
| 3 | Hold #3: 15000 | 100000 | 55000 | 45000 | 3 |
| 4 | Settle #2 | 80000 | 55000 | 25000 | 2 |
| 5 | Cancel #1 | 80000 | 65000 | 15000 | 1 |
| 6 | Settle #3 | 65000 | 65000 | 0 | 0 |

**Проверки:**
- [ ] Каждый hold независимо влияет на availableBalance
- [ ] Settle/Cancel конкретного hold не влияет на другие

---

### 7.4 BAL-004: System-wide Reconciliation

**Цель:** Проверить что сумма всех балансов = 0

**Предусловия:**
- Выполнены различные операции в системе

**Шаги:**

| Шаг | Проверка | Ожидаемый результат |
|-----|----------|---------------------|
| 1 | SUM(all wallet balances) | 0 |
| 2 | SUM(USER balances) + SUM(MERCHANT balances) + SUM(ESCROW) + SUM(SYSTEM) + SUM(DEPOSIT) + SUM(WITHDRAWAL) | 0 |

**Проверки:**
- [ ] Общая сумма всех кошельков = 0
- [ ] Сумма внутренних = -сумма внешних (DEPOSIT + WITHDRAWAL)

---

## 8. Settlement Tests

### 8.1 SET-001: Settlement одного заказа

**Цель:** Проверить settlement с комиссией для одного заказа

**Предусловия:**
- Order: 10000 (уже на ESCROW)
- Commission rate: 3%

**Расчёт:**
- Gross: 10000
- Fee: 10000 × 0.03 = 300
- Net: 10000 - 300 = 9700

**Шаги:**

| Шаг | Операция | ESCROW | MERCHANT_1 | SYSTEM | Zero-Sum |
|-----|----------|--------|------------|--------|----------|
| 0 | Initial (after buyer settle) | 10000 | 0 | 0 | ✓ |
| 1 | Settlement | 0 | 9700 | 300 | ✓ |

**Проверки:**
- [ ] MERCHANT_1 получил net amount (9700)
- [ ] SYSTEM получил fee (300)
- [ ] ESCROW.balance = 0
- [ ] Settlement entity создан с корректными суммами

---

### 8.2 SET-002: Batch Settlement (несколько заказов)

**Цель:** Проверить settlement нескольких заказов одного мерчанта

**Предусловия:**
- Order_1: 10000 на ESCROW
- Order_2: 5000 на ESCROW
- Order_3: 3000 на ESCROW
- Все для MERCHANT_1

**Расчёт:**
- Gross: 10000 + 5000 + 3000 = 18000
- Fee: 18000 × 0.03 = 540
- Net: 18000 - 540 = 17460

**Шаги:**

| Шаг | Операция | ESCROW | MERCHANT_1 | SYSTEM | Orders in Settlement |
|-----|----------|--------|------------|--------|----------------------|
| 0 | Initial | 18000 | 0 | 0 | 0 |
| 1 | Batch Settlement | 0 | 17460 | 540 | 3 |

**Проверки:**
- [ ] Все 3 заказа включены в один settlement
- [ ] Settlement.groupCount = 3
- [ ] Settlement.totalAmount = 18000
- [ ] Settlement.feeAmount = 540
- [ ] Settlement.netAmount = 17460
- [ ] SettlementTransactionGroup содержит 3 записи

---

### 8.3 SET-003: Settlement только своих заказов

**Цель:** Проверить что settlement не включает заказы других мерчантов

**Предусловия:**
- Order_1: 10000 для MERCHANT_1
- Order_2: 8000 для MERCHANT_2
- Оба на ESCROW

**Шаги:**

| Шаг | Операция | ESCROW | MERCHANT_1 | MERCHANT_2 | SYSTEM |
|-----|----------|--------|------------|------------|--------|
| 0 | Initial | 18000 | 0 | 0 | 0 |
| 1 | Settlement MERCHANT_1 | 8000 | 9700 | 0 | 300 |
| 2 | Settlement MERCHANT_2 | 0 | 9700 | 7760 | 540 |

**Проверки:**
- [ ] Каждый settlement включает только заказы своего мерчанта
- [ ] ESCROW корректно уменьшается

---

### 8.4 SET-004: Commission Rounding (HALF_UP)

**Цель:** Проверить округление комиссии

**Тестовые суммы:**

| Gross | Fee (3%) | Fee Rounded | Net |
|-------|----------|-------------|-----|
| 100 | 3.00 | 3 | 97 |
| 101 | 3.03 | 3 | 98 |
| 102 | 3.06 | 3 | 99 |
| 105 | 3.15 | 3 | 102 |
| 117 | 3.51 | 4 | 113 |
| 150 | 4.50 | 5 | 145 |
| 167 | 5.01 | 5 | 162 |

**Проверки:**
- [ ] Округление по HALF_UP
- [ ] fee + net = gross (всегда)

---

## 9. Refund Tests

### 9.1 REF-001: Full Refund после Settlement

**Цель:** Проверить полный возврат после settlement

**Предусловия:**
- Order: 10000
- Settlement выполнен: MERCHANT_1 получил 9700, SYSTEM получил 300
- BUYER_1 заплатил: 10000

**Шаги:**

| Шаг | Операция | BUYER_1 | MERCHANT_1 | SYSTEM | Refund Status |
|-----|----------|---------|------------|--------|---------------|
| 0 | After settlement | 90000 | 9700 | 300 | - |
| 1 | Request refund | 90000 | 9700 | 300 | PENDING |
| 2 | Execute refund | 99700 | 0 | 300 | COMPLETED |

**Проверки:**
- [ ] BUYER_1 получил net amount (9700), не gross (10000)
- [ ] MERCHANT_1.balance = 0
- [ ] SYSTEM сохранил комиссию (300)
- [ ] Refund.status = COMPLETED
- [ ] Транзакции со статусом REFUNDED созданы

---

### 9.2 REF-002: Refund с недостаточным балансом (PENDING_FUNDS)

**Цель:** Проверить поведение при недостатке средств у мерчанта

**Предусловия:**
- MERCHANT_1 balance: 5000 (вывел часть)
- Запрошен refund: 9700

**Шаги:**

| Шаг | Операция | MERCHANT_1 | BUYER_1 | Refund Status |
|-----|----------|------------|---------|---------------|
| 0 | Initial | 5000 | 90000 | - |
| 1 | Request refund 9700 | 5000 | 90000 | PENDING_FUNDS |
| 2 | Deposit 5000 to MERCHANT | 10000 | 90000 | PENDING_FUNDS |
| 3 | Auto-execute | 300 | 99700 | COMPLETED |

**Проверки:**
- [ ] Refund создан со статусом PENDING_FUNDS
- [ ] После пополнения баланса — auto-execute
- [ ] BUYER_1 получил полный refund

---

### 9.3 REF-003: Refund в пределах временного окна

**Цель:** Проверить refund в последний допустимый день

**Предусловия:**
- Settlement date: Day 0
- Refund window: 30 days
- Current date: Day 30

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Request refund on Day 30 | COMPLETED (в пределах окна) |

**Проверки:**
- [ ] Refund выполнен успешно
- [ ] День 30 входит в допустимое окно

---

### 9.4 REF-004: Refund вне временного окна

**Цель:** Проверить отклонение refund после истечения окна

**Предусловия:**
- Settlement date: Day 0
- Refund window: 30 days
- Current date: Day 31

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Request refund on Day 31 | REJECTED (expired) |

**Проверки:**
- [ ] Refund отклонён
- [ ] Код ошибки: REFUND_WINDOW_EXPIRED

---

### 9.5 REF-005: Refund прямой перевод (минуя ESCROW)

**Цель:** Проверить что refund идёт напрямую MERCHANT → BUYER

**Предусловия:**
- Settlement выполнен
- MERCHANT_1 balance: 9700

**Шаги:**

| Шаг | Операция | MERCHANT_1 | ESCROW | BUYER_1 |
|-----|----------|------------|--------|---------|
| 0 | After settlement | 9700 | 0 | 90000 |
| 1 | Refund | 0 | 0 | 99700 |

**Проверки:**
- [ ] ESCROW.balance не изменился (остался 0)
- [ ] Прямой перевод MERCHANT → BUYER
- [ ] Транзакции: MERCHANT -9700 (REFUNDED), BUYER +9700 (REFUNDED)

---

## 10. Multi-Party Tests

### 10.1 MPT-001: Несколько покупателей → Один мерчант

**Цель:** Проверить batch settlement от нескольких покупателей

**Предусловия:**
- BUYER_1 balance: 100000
- BUYER_2 balance: 50000
- MERCHANT_1 balance: 0

**Шаги:**

| Шаг | Операция | BUYER_1 | BUYER_2 | ESCROW | MERCHANT_1 | SYSTEM |
|-----|----------|---------|---------|--------|------------|--------|
| 0 | Initial | 100000 | 50000 | 0 | 0 | 0 |
| 1 | Order_1: BUYER_1 pays 20000 | 80000 | 50000 | 20000 | 0 | 0 |
| 2 | Order_2: BUYER_2 pays 15000 | 80000 | 35000 | 35000 | 0 | 0 |
| 3 | Order_3: BUYER_1 pays 10000 | 70000 | 35000 | 45000 | 0 | 0 |
| 4 | Batch Settlement | 70000 | 35000 | 0 | 43650 | 1350 |

**Расчёт:**
- Total: 20000 + 15000 + 10000 = 45000
- Fee: 45000 × 0.03 = 1350
- Net: 45000 - 1350 = 43650

**Проверки:**
- [ ] Settlement включил все 3 заказа
- [ ] Комиссия рассчитана от общей суммы
- [ ] Zero-sum сохранён

---

### 10.2 MPT-002: Один покупатель → Несколько мерчантов

**Цель:** Проверить раздельный settlement для разных мерчантов

**Предусловия:**
- BUYER_1 balance: 100000
- MERCHANT_1, MERCHANT_2 balance: 0

**Шаги:**

| Шаг | Операция | BUYER_1 | ESCROW | MERCHANT_1 | MERCHANT_2 | SYSTEM |
|-----|----------|---------|--------|------------|------------|--------|
| 0 | Initial | 100000 | 0 | 0 | 0 | 0 |
| 1 | Order_1: 30000 to M1 | 70000 | 30000 | 0 | 0 | 0 |
| 2 | Order_2: 20000 to M2 | 50000 | 50000 | 0 | 0 | 0 |
| 3 | Settlement M1 | 50000 | 20000 | 29100 | 0 | 900 |
| 4 | Settlement M2 | 50000 | 0 | 29100 | 19400 | 1500 |

**Проверки:**
- [ ] Каждый settlement независимый
- [ ] Комиссии рассчитаны отдельно

---

### 10.3 MPT-003: Marketplace сценарий (N buyers × M merchants)

**Цель:** Проверить сложный сценарий маркетплейса

**Предусловия:**
- BUYER_1: 100000, BUYER_2: 50000
- MERCHANT_1: 0, MERCHANT_2: 0

**Шаги:**

| Шаг | Операция | B1 | B2 | ESCROW | M1 | M2 | SYS | Zero |
|-----|----------|----|----|--------|----|----|-----|------|
| 0 | Initial | 100000 | 50000 | 0 | 0 | 0 | 0 | ✓ |
| 1 | B1→M1: 25000 | 75000 | 50000 | 25000 | 0 | 0 | 0 | ✓ |
| 2 | B2→M2: 15000 | 75000 | 35000 | 40000 | 0 | 0 | 0 | ✓ |
| 3 | B1→M2: 10000 | 65000 | 35000 | 50000 | 0 | 0 | 0 | ✓ |
| 4 | B2→M1: 8000 | 65000 | 27000 | 58000 | 0 | 0 | 0 | ✓ |
| 5 | Settle M1 (25k+8k) | 65000 | 27000 | 25000 | 32010 | 0 | 990 | ✓ |
| 6 | Settle M2 (15k+10k) | 65000 | 27000 | 0 | 32010 | 24250 | 1740 | ✓ |

**Расчёт M1:** 33000 × 0.97 = 32010, fee = 990
**Расчёт M2:** 25000 × 0.97 = 24250, fee = 750 (total sys = 1740)

**Проверки:**
- [ ] Каждый settlement независимый
- [ ] Все заказы учтены в правильных settlement
- [ ] Zero-sum на каждом шаге

---

### 10.4 MPT-004: Частичный Refund в Marketplace

**Цель:** Проверить refund одного заказа из batch settlement

**Предусловия:**
- После MPT-003: MERCHANT_1 balance = 32010
- Нужен refund Order_1 (25000 gross, 24250 net)

**Шаги:**

| Шаг | Операция | B1 | M1 | SYS |
|-----|----------|----|----|-----|
| 0 | After settlement | 65000 | 32010 | 990 |
| 1 | Refund Order_1 (24250 net) | 89250 | 7760 | 990 |

**Проверки:**
- [ ] Refund только за Order_1
- [ ] MERCHANT_1 остался с net от Order_4 (8000 × 0.97 = 7760)
- [ ] Комиссия осталась в SYSTEM

---

## 11. Edge Cases

### 11.1 EDGE-001: Минимальная сумма (1 цент)

**Цель:** Проверить операции с минимальной суммой

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | Amount | Fee | Net | Result |
|-----|----------|--------|-----|-----|--------|
| 1 | Payment 1 cent | 1 | 0 | 1 | Success |
| 2 | Settlement | 1 | 0 | 1 | Success |

**Расчёт:** 1 × 0.03 = 0.03 → rounds to 0

**Проверки:**
- [ ] Операция выполнена
- [ ] Комиссия = 0 (округление)
- [ ] Net = gross при минимальной сумме

---

### 11.2 EDGE-002: Комиссия при малых суммах

**Цель:** Проверить пороговые значения комиссии

| Amount | Fee (3%) | Fee Rounded | Net |
|--------|----------|-------------|-----|
| 1 | 0.03 | 0 | 1 |
| 16 | 0.48 | 0 | 16 |
| 17 | 0.51 | 1 | 16 |
| 33 | 0.99 | 1 | 32 |
| 34 | 1.02 | 1 | 33 |
| 50 | 1.50 | 2 | 48 |

**Проверки:**
- [ ] Порог комиссии: 17 центов (первая ненулевая комиссия)
- [ ] fee + net = gross

---

### 11.3 EDGE-003: Перевод всего баланса

**Цель:** Проверить перевод ровно всего доступного баланса

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | BUYER_1 | MERCHANT_1 |
|-----|----------|---------|------------|
| 0 | Initial | 100000 | 0 |
| 1 | Transfer 100000 | 0 | 100000 |

**Проверки:**
- [ ] Операция успешна
- [ ] BUYER_1.balance = 0 (ровно ноль, не отрицательный)

---

### 11.4 EDGE-004: На 1 цент больше баланса

**Цель:** Проверить отклонение при превышении баланса

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Transfer 100001 | Error: Insufficient funds |

**Проверки:**
- [ ] Операция отклонена
- [ ] Баланс не изменился

---

### 11.5 EDGE-005: Hold всего баланса

**Цель:** Проверить блокировку всего баланса

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | Total | Available | Held |
|-----|----------|-------|-----------|------|
| 0 | Initial | 100000 | 100000 | 0 |
| 1 | Hold 100000 | 100000 | 0 | 100000 |
| 2 | Try Hold 1 | Error | 0 | 100000 |

**Проверки:**
- [ ] Первый hold успешен
- [ ] Второй hold отклонён (available = 0)

---

### 11.6 EDGE-006: Два Hold одновременно (конкуренция)

**Цель:** Проверить защиту от race condition

**Предусловия:**
- BUYER_1 balance: 100000

**Сценарий:**
```
Thread 1: Hold 60000
Thread 2: Hold 60000
Оба стартуют одновременно
```

**Ожидаемый результат:**
- Один hold успешен
- Второй отклонён (insufficient funds)
- Не должно быть overdraft

**Проверки:**
- [ ] Только один hold выполнен
- [ ] availableBalance ≥ 0

---

### 11.7 EDGE-007: Settlement во время активного Hold

**Цель:** Проверить что активный hold не включается в settlement

**Предусловия:**
- Order_1: 10000 (SETTLED, готов к settlement)
- Order_2: 5000 (HOLD, ещё не settled)

**Шаги:**

| Шаг | Операция | Settlement включает |
|-----|----------|---------------------|
| 1 | Run Settlement | Только Order_1 |

**Проверки:**
- [ ] Settlement включил только SETTLED заказы
- [ ] HOLD заказы не затронуты

---

### 11.8 EDGE-008: Refund равный netAmount

**Цель:** Проверить refund на полную сумму (граничный случай)

**Предусловия:**
- MERCHANT_1 balance: 9700 (точно netAmount)
- Запрошен refund: 9700

**Шаги:**

| Шаг | Операция | MERCHANT_1 |
|-----|----------|------------|
| 0 | Initial | 9700 |
| 1 | Refund 9700 | 0 |

**Проверки:**
- [ ] Refund успешен
- [ ] MERCHANT_1.balance = 0 (ровно ноль)

---

### 11.9 EDGE-009: Refund больше netAmount

**Цель:** Проверить отклонение refund превышающего netAmount

**Предусловия:**
- Order netAmount: 9700
- Запрошен refund: 9701

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Refund 9701 | Error: Exceeds net amount |

**Проверки:**
- [ ] Refund отклонён
- [ ] Балансы не изменились

---

### 11.10 EDGE-010: Валюта — запрет cross-currency

**Цель:** Проверить запрет операций между разными валютами

**Предусловия:**
- BUYER_1 (USD): 100000
- MERCHANT_EUR (EUR): 0

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Transfer USD → EUR wallet | Error: Currency mismatch |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: CURRENCY_MISMATCH
- [ ] Балансы не изменились

---

## 12. Negative Scenarios

### 12.1 NEG-001: Недостаточно средств для платежа

**Предусловия:**
- BUYER_1 balance: 100

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Payment 1000 | Error: Insufficient funds |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: INSUFFICIENT_FUNDS
- [ ] Транзакции не созданы

---

### 12.2 NEG-002: Недостаточно available для Hold

**Предусловия:**
- BUYER_1 total: 100000
- BUYER_1 held: 90000
- BUYER_1 available: 10000

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Hold 20000 | Error: Insufficient available balance |

**Проверки:**
- [ ] Hold отклонён
- [ ] Проверяется available, не total

---

### 12.3 NEG-003: Платёж на несуществующий кошелёк

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Transfer to wallet_id=999999 | Error: Wallet not found |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: WALLET_NOT_FOUND

---

### 12.4 NEG-004: Нулевая сумма

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Payment amount=0 | Error: Invalid amount |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: INVALID_AMOUNT

---

### 12.5 NEG-005: Отрицательная сумма

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Payment amount=-100 | Error: Invalid amount |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: INVALID_AMOUNT

---

### 12.6 NEG-006: Settle уже settled группу

**Предусловия:**
- TransactionGroup status: SETTLED

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Settle(groupId) | Error: Already settled |

**Проверки:**
- [ ] Операция отклонена
- [ ] Код ошибки: INVALID_STATUS_TRANSITION

---

### 12.7 NEG-007: Cancel уже settled группу

**Предусловия:**
- TransactionGroup status: SETTLED

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Cancel(groupId) | Error: Cannot cancel settled group |

**Проверки:**
- [ ] Операция отклонена
- [ ] Settled группы нельзя отменить

---

### 12.8 NEG-008: Release settled группу

**Предусловия:**
- TransactionGroup status: SETTLED

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Release(groupId) | Error: Cannot release settled group |

**Проверки:**
- [ ] Операция отклонена
- [ ] Для settled используется refund, не release

---

### 12.9 NEG-009: Refund для cancelled заказа

**Предусловия:**
- TransactionGroup status: CANCELLED

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Refund(orderId) | Error: Order not settled |

**Проверки:**
- [ ] Refund только для SETTLED заказов
- [ ] CANCELLED заказы уже вернули деньги через cancel

---

### 12.10 NEG-010: Refund для HOLD заказа

**Предусловия:**
- TransactionGroup status: HOLD (ещё на escrow)

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Refund(orderId) | Error: Order not settled, use cancel |

**Проверки:**
- [ ] Для HOLD используется cancel, не refund
- [ ] Сообщение подсказывает правильное действие

---

### 12.11 NEG-011: Double Settlement (один заказ дважды)

**Предусловия:**
- Order_1 уже включён в Settlement_1

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Include Order_1 in Settlement_2 | Error: Already settled |

**Проверки:**
- [ ] Unique constraint на transaction_group_id
- [ ] Один заказ = один settlement

---

### 12.12 NEG-012: Double Refund (один заказ дважды)

**Предусловия:**
- Order_1 уже refunded (full refund)

**Шаги:**

| Шаг | Операция | Результат |
|-----|----------|-----------|
| 1 | Refund(Order_1) again | Error: Already refunded |

**Проверки:**
- [ ] Full refund можно сделать только один раз
- [ ] Код ошибки: ALREADY_REFUNDED

---

## 13. Idempotency Tests

### 13.1 IDEM-001: Повторный Payment (тот же idempotency key)

**Предусловия:**
- BUYER_1 balance: 100000

**Шаги:**

| Шаг | Операция | BUYER_1 | Result |
|-----|----------|---------|--------|
| 1 | Payment 10000, key="pay_123" | 90000 | Created |
| 2 | Payment 10000, key="pay_123" | 90000 | Returned existing |

**Проверки:**
- [ ] Второй запрос возвращает существующую транзакцию
- [ ] Баланс списан только один раз
- [ ] Response содержит флаг idempotent=true

---

### 13.2 IDEM-002: Разные суммы с тем же key

**Шаги:**

| Шаг | Операция | Result |
|-----|----------|--------|
| 1 | Payment 10000, key="pay_456" | Created |
| 2 | Payment 20000, key="pay_456" | Returns original (10000) |

**Проверки:**
- [ ] Возвращается оригинальная транзакция
- [ ] Новая транзакция не создаётся
- [ ] Warning в логах о несовпадении параметров

---

### 13.3 IDEM-003: Повторный Settlement Request

**Предусловия:**
- Settlement_1 уже выполнен для MERCHANT_1

**Шаги:**

| Шаг | Операция | Result |
|-----|----------|--------|
| 1 | Settlement MERCHANT_1 | Settlement_1 created |
| 2 | Settlement MERCHANT_1 (immediate retry) | Returns Settlement_1 |

**Проверки:**
- [ ] Нет double settlement
- [ ] Возвращается существующий settlement

---

### 13.4 IDEM-004: Повторный Refund Request

**Предусловия:**
- Refund уже выполнен для Order_1

**Шаги:**

| Шаг | Операция | Result |
|-----|----------|--------|
| 1 | Refund Order_1, key="ref_789" | Created |
| 2 | Refund Order_1, key="ref_789" | Returns existing |

**Проверки:**
- [ ] Деньги возвращены только один раз
- [ ] Второй запрос возвращает существующий refund

---

## 14. Hold Expiration Tests (Future)

> **Note:** Эти тесты для будущей функциональности автоматической отмены hold по таймауту.

### 14.1 EXP-001: Auto-Cancel после истечения Hold

**Предусловия:**
- Hold создан с expiration = 24 hours
- Current time = creation + 25 hours

**Ожидаемое поведение:**

| Шаг | Операция | BUYER_1 total | BUYER_1 avail | Status |
|-----|----------|---------------|---------------|--------|
| 0 | Hold created | 100000 | 90000 | HOLD |
| 1 | Expiration job runs | 100000 | 100000 | CANCELLED |

**Проверки:**
- [ ] Hold автоматически отменён
- [ ] Созданы CANCELLED транзакции
- [ ] Available balance восстановлен

---

### 14.2 EXP-002: Settle до истечения Hold

**Предусловия:**
- Hold создан с expiration = 24 hours
- Current time = creation + 12 hours

**Шаги:**

| Шаг | Операция | Result |
|-----|----------|--------|
| 1 | Settle before expiration | Success |

**Проверки:**
- [ ] Settlement успешен
- [ ] Expiration timer отменён

---

### 14.3 EXP-003: Попытка Settle после истечения

**Предусловия:**
- Hold истёк и был auto-cancelled

**Шаги:**

| Шаг | Операция | Result |
|-----|----------|--------|
| 1 | Try to settle expired hold | Error: Hold expired/cancelled |

**Проверки:**
- [ ] Settlement отклонён
- [ ] Нельзя settle отменённый hold

---

## Приложение A: Формулы расчётов

### Комиссия Settlement

```
fee = ROUND(gross × commission_rate, HALF_UP)
net = gross - fee
```

### Balance расчёты

```
totalBalance = SUM(amount) WHERE status IN (SETTLED, RELEASED, CANCELLED, REFUNDED)
heldAmount = ABS(SUM(amount)) WHERE status = HOLD AND amount < 0
availableBalance = totalBalance - heldAmount
```

### Zero-Sum проверка

```
SUM(all transactions in system) = 0
SUM(all transactions in group) = 0
SUM(all wallet balances) = 0
```

---

## Приложение B: Статусные переходы

### TransactionGroup Status

```
         ┌─────────────┐
         │ IN_PROGRESS │
         └──────┬──────┘
                │
       ┌────────┼────────┐
       ▼        ▼        ▼
  ┌────────┐ ┌────────┐ ┌──────────┐
  │SETTLED │ │RELEASED│ │CANCELLED │
  └────────┘ └────────┘ └──────────┘
```

### Допустимые переходы

| From | To | Trigger |
|------|----|---------|
| IN_PROGRESS | SETTLED | settle() |
| IN_PROGRESS | RELEASED | release() |
| IN_PROGRESS | CANCELLED | cancel() |
| SETTLED | - | (terminal, use refund for returns) |
| RELEASED | - | (terminal) |
| CANCELLED | - | (terminal) |

---

## Приложение C: Чеклист запуска тестов

### Перед запуском

- [ ] База данных очищена
- [ ] Системные кошельки созданы (ESCROW, SYSTEM, DEPOSIT, WITHDRAWAL)
- [ ] Тестовые пользователи созданы
- [ ] Начальные балансы установлены через DEPOSIT

### После каждого теста

- [ ] Zero-sum проверка пройдена
- [ ] Балансы соответствуют ожидаемым
- [ ] Статусы транзакций корректны
- [ ] Нет orphan транзакций (без группы)

### После всех тестов

- [ ] Общий reconciliation системы
- [ ] Проверка audit log
- [ ] Анализ производительности (опционально)

---

## История изменений

| Дата | Версия | Изменения |
|------|--------|-----------|
| 2025-01-15 | 1.0 | Первичная версия документа |