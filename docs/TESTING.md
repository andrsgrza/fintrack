# FinTrack — Test Catalog

Living document for all automated tests. Each entity gets its own section once ownership, validations, or domain rules are implemented.

**Related:** implementation status per entity → [`docs/IMPLEMENTATION.md`](IMPLEMENTATION.md) · validation rules per layer → [`docs/VALIDATIONS.md`](VALIDATIONS.md) · domain rules spec → [`docs/DOMAIN-RULES.md`](DOMAIN-RULES.md)

**Status legend**

| Symbol | Meaning                                     |
| ------ | ------------------------------------------- |
| ✅     | Implemented and passing                     |
| 🟡     | Partially covered (generated baseline only) |
| ⏳     | Planned, not implemented                    |
| ⏭️     | Skipped / disabled                          |
| —      | Not applicable for this entity              |

### Business validation error assertions (IT)

When asserting `BadRequestAlertException` from service rules (`400 invalid`):

| Assert      | Value                                     |
| ----------- | ----------------------------------------- |
| HTTP status | `400`                                     |
| `$.message` | `error.invalid`                           |
| `$.params`  | entity key (`tag`, `financialAccount`, …) |

Do **not** assert `$.title` — JHipster responds with `title: "Bad Request"`; the business message is not exposed there.

---

## How to run tests

### Backend — ownership suites

**FinancialAccount (pattern A — direct `user`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FinancialAccountResourceIT,FinancialAccountServiceTest,CurrentUserServiceTest test
```

**FinancialTransaction (pattern B — via `account`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FinancialTransactionResourceIT,FinancialTransactionServiceTest,CurrentUserServiceTest test
```

**Tag (pattern A — direct `user`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TagResourceIT,TagServiceTest,CurrentUserServiceTest test
```

**Tag timestamp/UX hardening (server-owned timestamps + simple CRUD UI):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TagResourceIT,TagServiceTest test
npm run jest -- tag
```

**Validation hardening — Tag + FinancialAccount (2026-07-10):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TagResourceIT,TagServiceTest,FinancialAccountResourceIT,FinancialAccountServiceTest test
```

**200 tests** — Tag name uniqueness (trim + case-insensitive per owner); FinancialAccount immutables (`currency`, `accountType`); PATCH JsonNode semantics.

**Category (pattern A — direct `user` + hierarchy):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=CategoryResourceIT,CategoryServiceTest,CurrentUserServiceTest test
```

**Budget (pattern A — direct `user` + M2M links):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=BudgetResourceIT,BudgetServiceTest,CurrentUserServiceTest test
```

**FinancialSubscription (pattern A — direct `user` + links):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FinancialSubscriptionResourceIT,FinancialSubscriptionServiceTest,CurrentUserServiceTest test
```

**TransactionRule (pattern A — direct `user` + resulting links):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TransactionRuleResourceIT,TransactionRuleServiceTest,CurrentUserServiceTest test
```

**TransactionRuleCondition (pattern C — via `transactionRule`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TransactionRuleConditionResourceIT,TransactionRuleConditionServiceTest,CurrentUserServiceTest test
```

**CreditAccountDetails (pattern B — via `account`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=CreditAccountDetailsResourceIT,CreditAccountDetailsServiceTest,CurrentUserServiceTest test
```

**ApiAccessToken (pattern A — direct `user` + token security baseline):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=ApiAccessTokenResourceIT,ApiAccessTokenServiceTest,CurrentUserServiceTest test
```

**ApiAccessTokenPermission (pattern C — via `apiAccessToken`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=ApiAccessTokenPermissionResourceIT,ApiAccessTokenPermissionServiceTest,CurrentUserServiceTest test
```

**UserDashboardPreference (pattern A — direct `user` + 1:1 guard):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=UserDashboardPreferenceResourceIT,UserDashboardPreferenceServiceTest,CurrentUserServiceTest test
```

**InternalTransfer (pattern D — via both transaction legs):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=InternalTransferResourceIT,InternalTransferServiceTest,CurrentUserServiceTest test
```

**TransactionIngestion (pattern B — via `account`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TransactionIngestionResourceIT,TransactionIngestionServiceTest,CurrentUserServiceTest test
```

**FileIngestion (pattern C — via `transactionIngestion`):**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FileIngestionResourceIT,FileIngestionServiceTest,CurrentUserServiceTest test
```

**ApiIngestion (pattern C + token snapshots 11C — via `transactionIngestion` + same-owner at create):**

```bash
./mvnw -Dskip.npm -Dskip.installnodenpm \
  -Dtest=ApiIngestionResourceIT,ApiIngestionServiceTest test
```

**IngestionRecord (pattern C + optional FT — via `transactionIngestion` + same-owner):**

```bash
./mvnw -Dskip.npm -Dskip.installnodenpm \
  -Dtest=IngestionRecordResourceIT,IngestionRecordServiceTest test
```

**All ownership pilots:** ~**1249** backend tests (… + 74 IR IT + 7 IR service + 1 FT helper IT; prior total ~1167).

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FinancialAccountResourceIT,FinancialAccountServiceTest,FinancialTransactionResourceIT,FinancialTransactionServiceTest,TagResourceIT,TagServiceTest,CategoryResourceIT,CategoryServiceTest,BudgetResourceIT,BudgetServiceTest,FinancialSubscriptionResourceIT,FinancialSubscriptionServiceTest,TransactionRuleResourceIT,TransactionRuleServiceTest,TransactionRuleConditionResourceIT,TransactionRuleConditionServiceTest,CreditAccountDetailsResourceIT,CreditAccountDetailsServiceTest,ApiAccessTokenResourceIT,ApiAccessTokenServiceTest,ApiAccessTokenPermissionResourceIT,ApiAccessTokenPermissionServiceTest,UserDashboardPreferenceResourceIT,UserDashboardPreferenceServiceTest,InternalTransferResourceIT,InternalTransferServiceTest,TransactionIngestionResourceIT,TransactionIngestionServiceTest,FileIngestionResourceIT,FileIngestionServiceTest,ApiIngestionResourceIT,ApiIngestionServiceTest,IngestionRecordResourceIT,IngestionRecordServiceTest,CurrentUserServiceTest test
```

### Backend — all tests

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm test
```

### E2E — single entity

```bash
# FinancialAccount
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/financial-account.cy.ts

# FinancialTransaction
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/financial-transaction.cy.ts

# Tag
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/tag.cy.ts

# Category
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/category.cy.ts

# Budget
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/budget.cy.ts

# FinancialSubscription
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/financial-subscription.cy.ts

# TransactionRule
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/transaction-rule.cy.ts

# CreditAccountDetails
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/credit-account-details.cy.ts

# ApiAccessToken
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/api-access-token.cy.ts

# ApiAccessTokenPermission
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/api-access-token-permission.cy.ts

# UserDashboardPreference
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/user-dashboard-preference.cy.ts

# InternalTransfer
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/internal-transfer.cy.ts

# IngestionRecord
./npmw run e2e:cypress -- --spec src/test/javascript/cypress/e2e/entity/ingestion-record.cy.ts
```

### E2E — full CI flow (app + Cypress)

```bash
./npmw run ci:e2e:dev
```

**E2E credentials** (Liquibase seed): `user`/`user`, `admin`/`admin`. Override with `E2E_USERNAME`, `E2E_PASSWORD`, `E2E_ADMIN_USERNAME`, `E2E_ADMIN_PASSWORD`.

---

## Test pyramid (project convention)

```
                    E2E Cypress
                 (few, UX + smoke)
              ─────────────────────
           Integration Resource IT
        (security, API, DB, criteria)
     ─────────────────────────────────
  Unit: Service / Foundation / Domain
 (isolated logic, mocks, JPA helpers)
```

| Layer                | What it proves                                                       | What it does _not_ replace  |
| -------------------- | -------------------------------------------------------------------- | --------------------------- |
| **Unit**             | Service rules with mocks; `equals`/relations; mapper round-trip      | Full HTTP + security wiring |
| **Integration (IT)** | REST contract, `@Valid`, ownership, criteria filters, DB persistence | Browser UI                  |
| **E2E**              | Menus, forms, multi-user flows in real UI                            | Exhaustive API edge cases   |

**Ownership patterns**

| Pattern                                        | Scope key                  | Entities                                                                                                                        | IT target        | Service unit target | E2E smoke |
| ---------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ---------------- | ------------------- | --------- |
| **A** — direct `user`                          | `user.login`               | FinancialAccount ✅, Tag ✅, Category ✅, …                                                                                     | 16 (+ hierarchy) | 9–11 tests          | 3 tests   |
| **A + M2M** — direct `user` + sets             | `user.login` + owned M2M   | Budget ✅                                                                                                                       | 16 + 4 links     | 11 tests            | 3 tests   |
| **A + links** — direct `user` + optional links | `user.login` + owned links | FinancialSubscription ✅, TransactionRule ✅                                                                                    | 16 + 9–10 links  | 12 tests            | 3 tests   |
| **B** — via parent                             | `account.user.login`       | FinancialTransaction ✅, CreditAccountDetails ✅, TransactionIngestion ✅                                                       | 15–16 tests      | 8–10 tests          | 3 tests   |
| **C** — via chain                              | parent → user              | TransactionRuleCondition ✅, FileIngestion ✅, ApiAccessTokenPermission ✅, ApiIngestion ✅ (11C snapshots), IngestionRecord ✅ | 15–18 tests      | 6–10 tests          | 3 tests   |

Replicate per entity: `CurrentUserService` → Repository scoped queries → Service → QueryService filter → Resource IT → Service unit → E2E smoke.

---

## Entity coverage matrix

| Entity                       | Unit domain | Unit mapper/DTO/criteria | Unit service | IT ownership | E2E ownership | Notes                                                                  |
| ---------------------------- | ----------- | ------------------------ | ------------ | ------------ | ------------- | ---------------------------------------------------------------------- |
| **FinancialAccount**         | ✅          | ✅                       | ✅           | ✅           | ✅            | Pilot pattern A — documented below                                     |
| **FinancialTransaction**     | ✅          | ✅                       | ✅           | ✅           | ✅            | Pilot pattern B — documented below                                     |
| **Tag**                      | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A clone — documented below                                     |
| **Category**                 | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A + hierarchy — documented below                               |
| **Budget**                   | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A + M2M — documented below                                     |
| **FinancialSubscription**    | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A + links — documented below                                   |
| **TransactionRule**          | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A + links; CRUD/domain baseline complete; rule engine deferred |
| **TransactionRuleCondition** | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern C; parent immutable; field/operator validations                |
| **CreditAccountDetails**     | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern B via account — documented below                               |
| **ApiAccessToken**           | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A + 11C delete/snapshots + reveal-once create                  |
| **ApiAccessTokenPermission** | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern C via token — documented below                                 |
| **UserDashboardPreference**  | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern A + 1:1 guard — documented below                               |
| **InternalTransfer**         | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern D via both tx legs — documented below                          |
| **TransactionIngestion**     | ✅          | ✅                       | ✅           | ✅           | ✅            | Pattern B via `account`; lifecycle + revert delete                     |
| **FileIngestion**            | ✅          | ✅                       | ✅           | ✅           | 🟡            | Pattern C via `transactionIngestion` — documented below                |
| **ApiIngestion**             | ✅          | ✅                       | ✅           | ✅           | 🟡            | Pattern C + token snapshots (11C) ✅                                   |
| **IngestionRecord**          | ✅          | ✅                       | ✅           | ✅           | 🟡            | Pattern C + optional FT + same-owner — documented below                |
| …                            |             |                          |              |              |               | Add rows as entities are hardened                                      |

---

## FinancialAccount

**Ownership model:** direct `user` (required). Normal users see/edit/delete only their accounts. `ROLE_ADMIN` bypasses filters.

**Domain rules in service:** assign `user` on create; ignore client `user`; preserve owner on update/patch; scope all reads/writes.

**Validation/domain rules in service:** `currency` and `accountType` **immutable** after create; `createdAt` and `updatedAt` are server-owned. Create ignores client timestamps and sets both to `now`; successful update/patch preserves `createdAt` and sets `updatedAt = now`; explicit null or changed timestamp on PUT/PATCH → `400 invalid`; same timestamp is accepted as a no-op. `initialBalance`, `initialBalanceDate`, `active` mutable. `initialBalance` is required and is the opening position (`posición inicial`) at the beginning of tracking; positive, zero, and negative values are allowed, with sign semantics depending on `accountType` (including `CREDIT_CARD` saldo a favor). There is no non-negative validation. `initialBalance` must have monetary `scale <= 2`; values with more decimals are rejected, not rounded. `initialBalanceDate` must be `<=` earliest transaction `transactionDate` when transactions exist. `active=false` has no side effects. DELETE is orchestrated through TransactionIngestion and FinancialTransaction delegates before account-level link cleanup. PATCH uses **JsonNode** — absent field preserves; explicit `currency`/`accountType`/timestamp null or different → `400 invalid`.

**Balance calculation status:** backend-only read model is implemented at `GET /api/financial-accounts/{id}/balance`. It is calculated on demand, not persisted, and uses `transactionDate` from `initialBalanceDate` through `asOfDate`. UI display, charts, dashboard aggregation, interest, statement cycles, and persisted balance recalculation remain deferred/open.

### Summary counts

| Type                   | File                                                    | Tests   | Custom vs generated                                                                                                                                                            |
| ---------------------- | ------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Integration IT         | `FinancialAccountResourceIT`                            | **145** | Custom ownership + immutability + timestamp hardening + initialBalance monetary scale + delete orchestration + date-floor + balance endpoint tests, plus JHipster CRUD/filters |
| Unit — service         | `FinancialAccountServiceTest`                           | **24**  | All custom (ownership + immutables + timestamp hardening + initialBalance monetary scale + delete orchestration + date-floor guard)                                            |
| Unit — balance service | `FinancialAccountBalanceServiceTest`                    | **8**   | All custom (access, transaction range, inactive/no-transaction behavior, credit details loading)                                                                               |
| Unit — calculators     | `Debit/Cash/CreditCard/InvestmentBalanceCalculatorTest` | **18**  | Formula coverage by account type, including credit-card saldo a favor and missing details                                                                                      |
| Unit — foundation      | `CurrentUserServiceTest`                                | **5**   | Shared; used by FA, FT, and future entities                                                                                                                                    |
| Unit — domain          | `FinancialAccountTest`                                  | **6**   | Generated (JPA relations)                                                                                                                                                      |
| Unit — mapper          | `FinancialAccountMapperTest`                            | **1**   | Generated                                                                                                                                                                      |
| Unit — DTO             | `FinancialAccountDTOTest`                               | **1**   | Generated                                                                                                                                                                      |
| Unit — criteria        | `FinancialAccountCriteriaTest`                          | **5**   | Generated                                                                                                                                                                      |
| E2E                    | `financial-account.cy.ts`                               | **10**  | 3 ownership + 7 CRUD/navigation                                                                                                                                                |

---

### 1. Integration tests — `FinancialAccountResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), embedded DB, full Spring context.

**Run:** `-Dtest=FinancialAccountResourceIT`

#### 1.1 Ownership & authorization (16) — ✅ custom

| Test                                                         | HTTP          | Actor   | Setup                                                  | Expected                            |
| ------------------------------------------------------------ | ------------- | ------- | ------------------------------------------------------ | ----------------------------------- |
| `createFinancialAccountAssignsCurrentUser`                   | `POST`        | `user`  | Payload includes another user's `user`                 | `201`; response `user.login = user` |
| `createFinancialAccountWithoutUserInPayloadSucceeds`         | `POST`        | `user`  | `user` null in DTO                                     | `201`; assigned to current user     |
| `getFinancialAccountOwnedByAnotherUserIsNotFound`            | `GET /:id`    | `user`  | Account owned by other user                            | `404`                               |
| `getAllFinancialAccountsDoesNotIncludeAnotherUsersAccounts`  | `GET`         | `user`  | Other user's account in DB                             | List excludes foreign id            |
| `getFinancialAccountCountDoesNotIncludeAnotherUsersAccounts` | `GET /count`  | `user`  | Other user's account in DB                             | Count `0`                           |
| `putFinancialAccountOwnedByAnotherUserIsNotFound`            | `PUT /:id`    | `user`  | Account owned by other                                 | `400` (`idnotfound`)                |
| `patchFinancialAccountOwnedByAnotherUserIsNotFound`          | `PATCH /:id`  | `user`  | Account owned by other                                 | `400` (`idnotfound`)                |
| `deleteFinancialAccountOwnedByAnotherUserIsNotFound`         | `DELETE /:id` | `user`  | Account owned by other                                 | `404`; row still exists             |
| `updateFinancialAccountCannotChangeOwner`                    | `PUT /:id`    | `user`  | Own account; DTO has other `user`                      | `200`; DB owner unchanged           |
| `patchFinancialAccountCannotChangeOwner`                     | `PATCH /:id`  | `user`  | Own account; minimal JSON with `name` + foreign `user` | `200`; DB owner unchanged           |
| `adminCanGetFinancialAccountOwnedByAnotherUser`              | `GET /:id`    | `admin` | Account owned by other                                 | `200`                               |
| `adminCanListAllFinancialAccountsIncludingOtherUsers`        | `GET`         | `admin` | Own + other user's accounts                            | Both ids in list                    |
| `adminCanCountAllFinancialAccountsIncludingOtherUsers`       | `GET /count`  | `admin` | 2 accounts (different owners)                          | Count `2`                           |
| `adminCanUpdateFinancialAccountOwnedByAnotherUser`           | `PUT /:id`    | `admin` | Account owned by other                                 | `200`; name updated                 |
| `adminCanDeleteFinancialAccountOwnedByAnotherUser`           | `DELETE /:id` | `admin` | Account owned by other                                 | `204`; row deleted                  |

#### 1.2 Immutability, timestamps, monetary scale & PATCH semantics (27) — ✅ custom

| Test                                                                                         | HTTP         | Setup                                      | Expected                                           |
| -------------------------------------------------------------------------------------------- | ------------ | ------------------------------------------ | -------------------------------------------------- |
| `putFinancialAccountCannotChangeCurrency`                                                    | `PUT /:id`   | DTO with different `currency`              | `400`; `error.invalid`; `params=financialAccount`  |
| `patchFinancialAccountCannotChangeCurrency`                                                  | `PATCH /:id` | Minimal JSON `{"currency":"USD"}`          | `400`; `error.invalid`                             |
| `putFinancialAccountCannotChangeAccountType`                                                 | `PUT /:id`   | DTO with different `accountType`           | `400`; `error.invalid`                             |
| `patchFinancialAccountCannotChangeAccountType`                                               | `PATCH /:id` | Minimal JSON `{"accountType":"CASH"}`      | `400`; `error.invalid`                             |
| `putFinancialAccountCannotChangeCreatedAt` / `putFinancialAccountCannotSetCreatedAtNull`     | `PUT /:id`   | Changed or null `createdAt`                | `400`; `error.invalid`                             |
| `putFinancialAccountCannotChangeUpdatedAt` / `putFinancialAccountCannotSetUpdatedAtNull`     | `PUT /:id`   | Changed or null `updatedAt`                | `400`; `error.invalid`                             |
| `patchFinancialAccountCannotChangeCreatedAt` / `patchFinancialAccountCannotSetCreatedAtNull` | `PATCH /:id` | Changed or null `createdAt`                | `400`; `error.invalid`                             |
| `patchFinancialAccountCannotChangeUpdatedAt` / `patchFinancialAccountCannotSetUpdatedAtNull` | `PATCH /:id` | Changed or null `updatedAt`                | `400`; `error.invalid`                             |
| `patchFinancialAccountWithSameTimestampsSucceedsAndUpdatesUpdatedAt`                         | `PATCH /:id` | Same timestamps + mutable field            | `200`; `createdAt` preserved; `updatedAt` advanced |
| `createDebitFinancialAccountWithZeroInitialBalanceSucceeds`                                  | `POST`       | `DEBIT`, `initialBalance = 0`              | `201`                                              |
| `createDebitFinancialAccountWithNegativeInitialBalanceSucceeds`                              | `POST`       | `DEBIT`, `initialBalance = -5000.25`       | `201`                                              |
| `createCreditCardFinancialAccountWithNegativeInitialBalanceSucceeds`                         | `POST`       | `CREDIT_CARD`, `initialBalance = -5000.25` | `201`; saldo a favor allowed                       |
| `createCashFinancialAccountWithNegativeScaleZeroInitialBalanceSucceeds`                      | `POST`       | `CASH`, `initialBalance = -1`              | `201`                                              |
| `createFinancialAccountWithInitialBalanceScaleGreaterThanTwoFails`                           | `POST`       | `initialBalance = 100.001`                 | `400`; `error.invalid`; no rounding                |
| `createFinancialAccountWithNegativeInitialBalanceScaleGreaterThanTwoFails`                   | `POST`       | `initialBalance = -5000.123`               | `400`; `error.invalid`; no rounding                |
| `putFinancialAccountCanChangeInitialBalanceToValidNegativeScaleTwo`                          | `PUT /:id`   | `initialBalance = -5000.25`                | `200`; balance updated                             |
| `putFinancialAccountCannotChangeInitialBalanceToScaleGreaterThanTwo`                         | `PUT /:id`   | `initialBalance = 100.001`                 | `400`; persisted balance unchanged                 |
| `patchFinancialAccountCanChangeInitialBalance`                                               | `PATCH /:id` | `{"initialBalance": 2}` only               | `200`; balance updated                             |
| `patchFinancialAccountCanChangeInitialBalanceToValidNegativeScaleTwo`                        | `PATCH /:id` | `{"initialBalance": -5000.25}`             | `200`; balance updated                             |
| `patchFinancialAccountCannotChangeInitialBalanceToScaleGreaterThanTwo`                       | `PATCH /:id` | `{"initialBalance": -5000.123}`            | `400`; persisted balance unchanged                 |
| `patchFinancialAccountOmittingInitialBalancePreservesExistingValue`                          | `PATCH /:id` | name-only patch                            | `200`; balance preserved                           |
| `patchFinancialAccountCanChangeInitialBalanceDate`                                           | `PATCH /:id` | `{"initialBalanceDate":"…"}` only          | `200`; date updated                                |
| `patchFinancialAccountCanChangeActive`                                                       | `PATCH /:id` | `{"active": true}` only                    | `200`; active updated                              |

#### 1.3 Delete orchestration & domain guards (10) — ✅ custom

| Test                                                                              | What it checks                                                            |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `deleteFinancialAccountWithCreditAccountDetailsDeletesDetails`                    | CAD row is removed through account orchestration                          |
| `deleteFinancialAccountLinkedToBudgetRemovesLinkAndPreservesBudget`               | Budget survives; account M2M link removed                                 |
| `deleteFinancialAccountLinkedToSubscriptionClearsAccountAndPreservesSubscription` | Subscription survives; `account = null`                                   |
| `deleteFinancialAccountWithManualTransactionDeletesTransactionAndPreservesTags`   | Remaining tx deleted; tags survive                                        |
| `deleteFinancialAccountWithInternalTransferPreservesOppositeTransaction`          | Transfer row deleted; tx on deleted account removed; opposite tx survives |
| `deleteFinancialAccountWithTransactionIngestionTreeDeletesTree`                   | File/API metadata, records, ingestion-linked txs, and parents removed     |
| `patchFinancialAccountActiveHasNoSideEffects`                                     | `active` update does not delete txs or unlink budgets/subscriptions       |
| `putInitialBalanceDateAfterEarliestTransactionDateFails`                          | PUT date floor violation → `400`                                          |
| `putInitialBalanceDateEqualOrBeforeEarliestTransactionDateSucceeds`               | Equal/before earliest transaction date accepted                           |
| `patchInitialBalanceDateAfterEarliestTransactionDateFails`                        | PATCH date floor violation → `400`                                        |

**PATCH note:** Successful PATCH tests send **minimal JSON** (`ObjectNode` with only the field under test). Full entity/DTO serialization includes `currency`/`accountType: null`, which JsonNode PATCH treats as an explicit change → `400`.

#### 1.4 Balance endpoint (7) — ✅ custom

| Test                                                            | What it checks                                                            |
| --------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `getBalanceForOwnDebitAccountReturnsCurrentBalance`             | Own DEBIT account returns `currentBalance = initialBalance + IN - OUT`    |
| `getBalanceForForeignAccountReturnsNotFoundForNormalUser`       | Normal user gets `404` for another user's account                         |
| `adminCanGetBalanceForForeignAccount`                           | Admin can calculate a foreign account balance                             |
| `getCreditCardBalanceReturnsCurrentDebtAndAvailableCredit`      | CREDIT_CARD returns `currentDebt` and `availableCredit` from credit limit |
| `getCreditCardBalanceWithoutDetailsReturnsMissingCreditDetails` | Missing CAD returns `missingCreditDetails=true`, no hard failure          |
| `getBalanceAsOfDateFiltersTransactions`                         | `asOfDate` excludes later transactions                                    |
| `getBalanceForAccountWithoutTransactionsReturnsTotalsZero`      | No-transaction account returns opening-position snapshot                  |

#### 1.5 CRUD & validation (11) — 🟡 JHipster generated

| Test                                                        | What it checks                                      |
| ----------------------------------------------------------- | --------------------------------------------------- |
| `createFinancialAccount`                                    | Happy-path `POST` → `201`, persisted fields         |
| `createFinancialAccountWithExistingId`                      | `POST` with id → `400`                              |
| `checkNameIsRequired`                                       | Missing name → `400`                                |
| `checkAccountTypeIsRequired`                                | Missing enum → `400`                                |
| `checkCurrencyIsRequired`                                   | Missing currency → `400`                            |
| `checkInitialBalanceIsRequired`                             | Missing balance → `400`                             |
| `checkInitialBalanceDateIsRequired`                         | Missing date → `400`                                |
| `checkActiveIsRequired`                                     | Missing active → `400`                              |
| `createFinancialAccountWithoutCreatedAtUsesServerTimestamp` | Missing `createdAt` accepted; server sets timestamp |
| `createFinancialAccountWithoutUpdatedAtUsesServerTimestamp` | Missing `updatedAt` accepted; server sets timestamp |
| `deleteFinancialAccount`                                    | Happy-path `DELETE` → `204`                         |

#### 1.6 Read & update (14) — 🟡 JHipster generated

| Test                                                        | What it checks                                           |
| ----------------------------------------------------------- | -------------------------------------------------------- |
| `getAllFinancialAccounts`                                   | List returns seeded account                              |
| `getFinancialAccount`                                       | `GET /:id` returns entity                                |
| `getNonExistingFinancialAccount`                            | Unknown id → `404`                                       |
| `putExistingFinancialAccount`                               | Full `PUT` update                                        |
| `putNonExistingFinancialAccount`                            | `PUT` unknown id → `400`                                 |
| `putWithIdMismatchFinancialAccount`                         | Path id ≠ body id → `400`                                |
| `putWithMissingIdPathParamFinancialAccount`                 | `PUT` without path id → `405`                            |
| `partialUpdateFinancialAccountWithPatch`                    | `PATCH` mutable fields only (minimal JSON)               |
| `fullUpdateFinancialAccountWithPatch`                       | `PATCH` all mutable fields (no `currency`/`accountType`) |
| `patchNonExistingFinancialAccount`                          | `PATCH` unknown id → `400`                               |
| `patchWithIdMismatchFinancialAccount`                       | Path/body id mismatch → `400`                            |
| `patchWithMissingIdPathParamFinancialAccount`               | `PATCH` without path → `405`                             |
| `getAllFinancialAccountsWithEagerRelationshipsIsEnabled`    | Mockito — eager load path                                |
| `getAllFinancialAccountsWithEagerRelationshipsIsNotEnabled` | Mockito — lazy path                                      |

#### 1.7 Criteria filters (60) — 🟡 JHipster generated

One test per filterable field (`name`, `institutionName`, `accountType`, `currency`, `initialBalance`, `initialBalanceDate`, `lastFourDigits`, `description`, `color`, `icon`, `active`, `createdAt`, `updatedAt`, `userId`, `budgets`, `transactionIngestions`). Each exercises `equals`, `in`, `specified`, `contains` / `doesNotContain`, or range operators where applicable.

**Note:** Ownership filter applies on top of criteria — user's list only searches their accounts (admin sees all).

---

### 2. Unit tests — service layer

#### 2.1 `FinancialAccountServiceTest` (24) — ✅ custom

Mocks: `FinancialAccountRepository`, `FinancialAccountMapper`, `CurrentUserService`, cleanup/delegate repositories/services. No Spring context.

| Test                                                                                | Rule under test                                                                              |
| ----------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `saveShouldAssignCurrentUser`                                                       | `save()` sets `entity.user` from `CurrentUserService`                                        |
| `saveShouldIgnoreClientProvidedTimestamps`                                          | `save()` overwrites client timestamps with server values                                     |
| `saveShouldAcceptInitialBalanceWithScaleZeroOneOrTwo`                               | scale 0/1/2 accepted                                                                         |
| `saveShouldAcceptNegativeInitialBalanceWithScaleZeroOneOrTwo`                       | negative scale 0/1/2 accepted                                                                |
| `saveShouldRejectInitialBalanceScaleGreaterThanTwoWithoutRounding`                  | scale > 2 rejected; original value not rounded                                               |
| `saveShouldRejectNullInitialBalance`                                                | service rejects null `initialBalance`                                                        |
| `updateShouldPreserveExistingOwner`                                                 | `update()` keeps original owner after mapper                                                 |
| `updateShouldRejectInitialBalanceScaleGreaterThanTwo`                               | update rejects scale > 2                                                                     |
| `updateShouldRejectChangedCreatedAt`                                                | `update()` rejects changed `createdAt`                                                       |
| `updateShouldRejectNullUpdatedAt`                                                   | `update()` rejects null `updatedAt`                                                          |
| `updateShouldFailWhenAccountIsNotAccessible`                                        | `update()` throws when scoped lookup empty                                                   |
| `updateShouldRejectCurrencyChange`                                                  | `update()` throws when `currency` differs                                                    |
| `updateShouldRejectAccountTypeChange`                                               | `update()` throws when `accountType` differs                                                 |
| `findOneShouldReturnEmptyForAnotherUsersAccount`                                    | Non-admin scoped `findOne` → empty                                                           |
| `findOneShouldUseAdminLookupWhenCurrentUserIsAdmin`                                 | Admin uses `findOneWithEagerRelationships`                                                   |
| `deleteShouldReturnFalseWhenAccountIsNotAccessible`                                 | `delete()` → `false`, no `deleteById`                                                        |
| `deleteShouldRemoveAccessibleAccount`                                               | `delete()` → `true`, delegates TI/FT cleanup, clears account-level links, calls `deleteById` |
| `updateShouldRejectInitialBalanceDateAfterEarliestTransactionDate`                  | `initialBalanceDate` after earliest transaction date → `IllegalArgumentException`            |
| `findAllWithEagerRelationshipsShouldScopeToCurrentUser`                             | Non-admin list uses `findAll...ByUserLogin`                                                  |
| `partialUpdateShouldReturnEmptyWhenAccountIsNotAccessible`                          | Scoped partial update → `Optional.empty()`                                                   |
| `partialUpdateShouldRejectExplicitNullCreatedAt`                                    | PATCH-style service call rejects `createdAt: null`                                           |
| `partialUpdateShouldRejectChangedUpdatedAt`                                         | PATCH-style service call rejects changed `updatedAt`                                         |
| `partialUpdateShouldAllowSameTimestampsAndSetUpdatedAt`                             | Same timestamps accepted; `updatedAt` set by server                                          |
| `partialUpdateShouldRejectExplicitInitialBalanceScaleGreaterThanTwoWithoutMutating` | explicit invalid PATCH balance is rejected before mutating the entity                        |

#### 2.2 `FinancialAccountBalanceServiceTest` (8) — ✅ custom

Mocks: `FinancialAccountService`, `FinancialTransactionRepository`, `CreditAccountDetailsRepository`, and calculator strategy list. No Spring context.

| Test                                                                          | Rule under test                                            |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------- |
| `calculateBalanceShouldUseAccessibleAccountAndDelegateToMatchingCalculator`   | Uses scoped account resolver and matching calculator       |
| `calculateBalanceShouldPropagateEmptyAccessibleLookup`                        | Foreign/inaccessible account returns empty                 |
| `calculateBalanceShouldUseAdminAccessibleAccountFromResolver`                 | Admin path is inherited from accessible account resolver   |
| `calculateBalanceShouldUseInitialBalanceWhenNoTransactionsExist`              | Empty tx list still delegates and returns opening snapshot |
| `calculateBalanceShouldLoadTransactionsFromInitialBalanceDateThroughAsOfDate` | Inclusive date range uses `transactionDate`                |
| `calculateBalanceShouldCalculateInactiveAccounts`                             | `active=false` still calculates                            |
| `calculateBalanceShouldHandleCreditCardWithoutDetails`                        | Missing card details are represented in DTO                |
| `calculateBalanceShouldLoadCreditDetailsForCreditCard`                        | Credit details are loaded only for card calculations       |

#### 2.3 Calculator unit tests (18) — ✅ custom

| File                              | Tests | Coverage                                                                                              |
| --------------------------------- | ----- | ----------------------------------------------------------------------------------------------------- |
| `DebitBalanceCalculatorTest`      | 5     | positive/zero/negative opening positions; IN/OUT formula; supports                                    |
| `CashBalanceCalculatorTest`       | 5     | positive/zero/negative cash positions; IN/OUT formula; supports                                       |
| `CreditCardBalanceCalculatorTest` | 6     | debt increases with OUT/decreases with IN; negative opening credit; available credit; missing details |
| `InvestmentBalanceCalculatorTest` | 2     | provisional currentBalance formula; supports                                                          |

### 3. Frontend tests — `financial-account-opening-position.spec.tsx`

The FinancialAccount UI spec covers dynamic opening-position labels/help text for create/detail views, account-type change reset behavior, preservation of unrelated create-form fields, hidden `createdAt` / `updatedAt` inputs, and disabled `accountType` / `currency` selects in edit mode.

#### 2.2 `CurrentUserServiceTest` (5) — ✅ custom (shared foundation)

| Test                                                | Rule under test                    |
| --------------------------------------------------- | ---------------------------------- |
| `getCurrentUserLoginShouldReturnAuthenticatedLogin` | Reads login from `SecurityContext` |
| `getCurrentUserLoginShouldFailWhenUnauthenticated`  | No auth → `IllegalStateException`  |
| `getCurrentUserShouldResolveUserFromRepository`     | Loads `User` by login              |
| `isAdminShouldReturnTrueForAdminAuthority`          | `ROLE_ADMIN` → `true`              |
| `isAdminShouldReturnFalseForRegularUser`            | `ROLE_USER` only → `false`         |

---

### 3. Unit tests — generated baseline

| File                           | Tests | Purpose                                                                                                                                             |
| ------------------------------ | ----- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FinancialAccountTest`         | 6     | `equals`/`hashCode`; bidirectional relations (`creditAccountDetails`, `financialTransactions`, `subscriptions`, `budgets`, `transactionIngestions`) |
| `FinancialAccountMapperTest`   | 1     | DTO → entity → DTO round-trip                                                                                                                       |
| `FinancialAccountDTOTest`      | 1     | DTO `equals` by id                                                                                                                                  |
| `FinancialAccountCriteriaTest` | 5     | Criteria builders, copy, fluent filters                                                                                                             |

**Value for ownership:** low. Keep for regression; do not rely on these for security.

---

### 4. E2E tests — `financial-account.cy.ts`

**Stack:** Cypress, real browser, JWT via `cy.login()`, API helpers via `cy.authenticatedRequest()`.

**Prerequisite:** Backend on `8080`, frontend dev server on `9000` (or CI e2e profile).

#### 4.1 Navigation & CRUD UI (7) — 🟡 / ✅

| Test                                                                   | What it checks                                                       |
| ---------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `FinancialAccounts menu should load FinancialAccounts page`            | Menu → list route loads                                              |
| `should load create FinancialAccount page`                             | Create button → form → cancel                                        |
| `detail button click should load details FinancialAccount page`        | Detail view (needs existing row)                                     |
| `edit button click should load edit FinancialAccount page and go back` | Edit form opens and cancels                                          |
| `edit button click should load edit FinancialAccount page and save`    | Edit save returns to list                                            |
| `last delete button click should delete instance of FinancialAccount`  | Delete dialog → `204`                                                |
| `should create an instance of FinancialAccount`                        | Full form submit → `201`; `user.login = user`; no `[data-cy="user"]` |

#### 4.2 Ownership smoke (3) — ✅ custom

| Test                                                    | What it checks                                   |
| ------------------------------------------------------- | ------------------------------------------------ |
| `should not render user selector on create form`        | `[data-cy="user"]` absent                        |
| `regular user should not see accounts created by admin` | API: admin creates → user `GET` list excludes it |
| `admin should see accounts created by another user`     | API: user creates → admin `GET` list includes it |

---

### 5. FinancialAccount — gaps & planned tests

| Priority | Layer | Test                                                                    | Status                           |
| -------- | ----- | ----------------------------------------------------------------------- | -------------------------------- |
| Medium   | IT    | `FinancialAccountQueryService` ownership filter in isolation            | ⏳                               |
| Medium   | IT    | Delete guard: account with transactions → `400`                         | ⏳ (domain rule not implemented) |
| Low      | Unit  | `FinancialAccountMapper` ignores `user` on `toEntity` / `partialUpdate` | ⏳                               |
| Low      | E2E   | UI list isolation: user A cannot see user B row in table (not just API) | ⏳                               |
| Low      | E2E   | Admin sees all rows in UI table                                         | ⏳                               |

---

## FinancialTransaction

**Ownership model:** indirect via `account` (required). Normal users see/edit/delete only transactions whose `account` they own. `ROLE_ADMIN` bypasses filters.

**Domain rules in service:** resolve `account` from DB (`findAccessibleAccountEntity`); validate optional `category` / `tags` / `subscription` against the transaction account owner; create/update/patch use presence-aware JSON semantics; `account`, `origin`, `transactionIngestion`, and server timestamps are immutable/server-owned; `amount > 0` with scale 2; category/subscription compatibility; delete cleanup for linked `IngestionRecord`, `InternalTransfer`, and tag joins.

### Summary counts

| Type            | File                               | Tests   | Custom vs generated                        |
| --------------- | ---------------------------------- | ------- | ------------------------------------------ |
| Integration IT  | `FinancialTransactionResourceIT`   | **101** | 25 custom + 76 JHipster/generated baseline |
| Unit — service  | `FinancialTransactionServiceTest`  | **10**  | All custom (ownership + domain)            |
| Unit — domain   | `FinancialTransactionTest`         | **9**   | Generated (JPA relations)                  |
| Unit — mapper   | `FinancialTransactionMapperTest`   | **1**   | Generated                                  |
| Unit — DTO      | `FinancialTransactionDTOTest`      | **1**   | Generated                                  |
| Unit — criteria | `FinancialTransactionCriteriaTest` | **5**   | Generated                                  |
| E2E             | `financial-transaction.cy.ts`      | **10**  | 3 ownership + 7 CRUD/navigation            |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FinancialTransactionResourceIT,FinancialTransactionServiceTest test
```

---

### 1. Integration tests — `FinancialTransactionResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL, full Spring context.

**Run:** `-Dtest=FinancialTransactionResourceIT`

**Test data:** `createEntity()` requires a `FinancialAccount` (via `FinancialAccountResourceIT.createEntity()`). `DEFAULT_AMOUNT = 1` (service rejects ≤ 0).

#### 1.1 Ownership & authorization (12) — ✅ custom

| Test                                                                 | HTTP          | Actor   | Setup                                | Expected                   |
| -------------------------------------------------------------------- | ------------- | ------- | ------------------------------------ | -------------------------- |
| `createFinancialTransactionOnInaccessibleAccountFails`               | `POST`        | `user`  | DTO points to another user's account | `400` (`invalid`)          |
| `getFinancialTransactionOnAnotherUsersAccountIsNotFound`             | `GET /:id`    | `user`  | Tx on other user's account           | `404`                      |
| `getAllFinancialTransactionsDoesNotIncludeAnotherUsersTransactions`  | `GET`         | `user`  | Other user's tx in DB                | List excludes foreign id   |
| `getFinancialTransactionCountDoesNotIncludeAnotherUsersTransactions` | `GET /count`  | `user`  | Other user's tx in DB                | Count `0`                  |
| `putFinancialTransactionOnAnotherUsersAccountIsNotFound`             | `PUT /:id`    | `user`  | Tx on other user's account           | `400` (`idnotfound`)       |
| `patchFinancialTransactionOnAnotherUsersAccountIsNotFound`           | `PATCH /:id`  | `user`  | Tx on other user's account           | `400` (`idnotfound`)       |
| `deleteFinancialTransactionOnAnotherUsersAccountIsNotFound`          | `DELETE /:id` | `user`  | Tx on other user's account           | `404`; row still exists    |
| `adminCanGetFinancialTransactionOnAnotherUsersAccount`               | `GET /:id`    | `admin` | Tx on other user's account           | `200`                      |
| `adminCanListAllFinancialTransactionsIncludingOtherUsers`            | `GET`         | `admin` | Own + other user's txs               | Both ids in list           |
| `adminCanCountAllFinancialTransactionsIncludingOtherUsers`           | `GET /count`  | `admin` | 2 txs (different account owners)     | Count `2`                  |
| `adminCanUpdateFinancialTransactionOwnedByAnotherUser`               | `PUT /:id`    | `admin` | Tx on other user's account           | `200`; description updated |
| `adminCanDeleteFinancialTransactionOwnedByAnotherUser`               | `DELETE /:id` | `admin` | Tx on other user's account           | `204`; row deleted         |

#### 1.2 Domain rules — ✅ custom

| Test                                                                          | HTTP     | What it checks                                                    |
| ----------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------- |
| `createFinancialTransactionPreservesPayloadOriginWhenNoIngestionIsSet`        | `POST`   | Valid payload origin is preserved when no ingestion is set        |
| `createFinancialTransactionWithZeroAmountFails`                               | `POST`   | `amount = 0` → `400`                                              |
| `createFinancialTransactionIgnoresMissingCreatedAtAndSetsServerTimestamp`     | `POST`   | Server owns `createdAt`                                           |
| `createFinancialTransactionIgnoresMissingUpdatedAtAndSetsServerTimestamp`     | `POST`   | Server owns `updatedAt`                                           |
| `createFinancialTransactionTrimsTextFieldsAndConvertsBlankOptionalTextToNull` | `POST`   | Trim required/optional text; blank optional → null                |
| `createFinancialTransactionWithIncompatibleCategoryTypeFails`                 | `POST`   | OUT + INCOME category → `400`                                     |
| `updateFinancialTransactionCannotChangeOrigin`                                | `PUT`    | Changing origin → `400`; DB unchanged                             |
| `patchFinancialTransactionAmountFailsWhenLinkedToInternalTransfer`            | `PATCH`  | Amount immutable while linked to transfer                         |
| `deleteFinancialTransactionLinkedToIngestionRecordRejectsAndUnlinksRecord`    | `DELETE` | FT deleted; record survives as REJECTED with manual-deleted error |

**Note:** Generated PUT/PATCH tests use minimal/presence-aware JSON for PATCH; explicit null for required/immutable fields is now meaningful and usually invalid.

#### 1.3 CRUD & validation — 🟡 JHipster generated (~76)

Same structure as FinancialAccount: happy-path CRUD, required-field checks, criteria filters per field (`transactionDate`, `amount`, `flow`, `origin`, `accountId`, `categoryId`, `tagsId`, …). Ownership filter applies on top — non-admin criteria only search their transactions.

---

### 2. Unit tests — `FinancialTransactionServiceTest` (10) — ✅ custom

Mocks: `FinancialTransactionRepository`, `FinancialTransactionMapper`, `FinancialAccountService`, `CategoryRepository`, `TagRepository`, `FinancialSubscriptionRepository`, `TransactionIngestionRepository`, `InternalTransferRepository`, `IngestionRecordRepository`, `CurrentUserService`. No Spring context.

| Test                                                                     | Rule under test                                                                                               |
| ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `saveShouldPreserveOriginResolveAccessibleAccountAndSetServerTimestamps` | Create resolves accessible account and sets server timestamps; `origin` / ingestion come from validated input |
| `saveShouldRejectNonPositiveAmount`                                      | `amount ≤ 0` → `IllegalArgumentException`                                                                     |
| `saveShouldFailWhenAccountIsNotAccessible`                               | Missing account → no save                                                                                     |
| `updateShouldPreserveOriginAndIngestion`                                 | Update keeps existing `origin`                                                                                |
| `updateShouldFailWhenTransactionIsNotAccessible`                         | Scoped update → `NoSuchElementException`                                                                      |
| `findOneShouldReturnEmptyForAnotherUsersTransaction`                     | Non-admin scoped `findOne` → empty                                                                            |
| `deleteShouldReturnFalseWhenTransactionIsNotAccessible`                  | `delete()` → `false`, no `deleteById`                                                                         |
| `deleteShouldRemoveAccessibleTransaction`                                | `delete()` → `true`, calls `deleteById`                                                                       |
| `findAllWithEagerRelationshipsShouldScopeToCurrentUser`                  | Non-admin list uses `findAll...ByAccountUserLogin`                                                            |
| `partialUpdateShouldReturnEmptyWhenTransactionIsNotAccessible`           | Scoped partial update → `Optional.empty()`                                                                    |

---

### 3. E2E tests — `financial-transaction.cy.ts`

**Stack:** Cypress, JWT via `cy.login()`, API helpers. **Prerequisite:** creates a `FinancialAccount` via API before each test (required relationship).

#### 3.1 Navigation & CRUD UI (7) — ✅

| Test                                                                       | What it checks                                                      |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `FinancialTransactions menu should load FinancialTransactions page`        | Menu → list route                                                   |
| `should load create FinancialTransaction page`                             | Create → form → cancel                                              |
| `detail button click should load details FinancialTransaction page`        | Detail view                                                         |
| `edit button click should load edit FinancialTransaction page and go back` | Edit form cancel                                                    |
| `edit button click should load edit FinancialTransaction page and save`    | Edit save                                                           |
| `last delete button click should delete instance of FinancialTransaction`  | Delete dialog → `204`                                               |
| `should create an instance of FinancialTransaction`                        | Current UI form create path → `201`; ingestion selector not covered |

#### 3.2 Ownership smoke (3) — ✅ custom

| Test                                                                | What it checks                                                  |
| ------------------------------------------------------------------- | --------------------------------------------------------------- |
| `should not render transaction ingestion on create form`            | `[data-cy="transactionIngestion"]` absent on `/new`             |
| `regular user should not see transactions on another users account` | API: admin creates tx on admin account → user `GET` excludes it |
| `admin should see transactions on another users account`            | API: user creates tx → admin `GET` includes it                  |

---

### 4. FinancialTransaction — gaps & planned tests

| Priority | Layer | Test                                                                           | Status |
| -------- | ----- | ------------------------------------------------------------------------------ | ------ |
| Medium   | IT    | Create with another user's `category` / `tag` / `subscription` → `400`         | ⏳     |
| Medium   | IT    | `FinancialTransactionQueryService` ownership filter in isolation               | ⏳     |
| Low      | Unit  | `FinancialTransactionMapper` ignores relations on `toEntity` / `partialUpdate` | ⏳     |
| Low      | E2E   | UI list isolation (table rows, not just API)                                   | ⏳     |
| Low      | E2E   | Edit form: `transactionIngestion` read-only for imported tx                    | ⏳     |

---

## Tag

**Ownership model:** direct `user` (required). Normal users see/edit/delete only their tags. `ROLE_ADMIN` bypasses filters.

**Domain rules:** DELETE allowed when in use — unlink from `FinancialTransaction.tags`, `TransactionRule.resultingTags`, `FinancialSubscription.tags`, `Budget.tags` (join rows only), then delete tag. Related entities survive. `active=false` keeps links. `createdAt` / `updatedAt` are server-owned: create ignores client timestamps; PUT/PATCH preserve `createdAt`, reject changed/null timestamp fields, and set `updatedAt = now`. See [`DOMAIN-RULES.md` §4](DOMAIN-RULES.md#4-tag).

**Frontend UX:** Tag create/edit shows only `name`, `description`, `color`, `active`. It does not show/send `user`, `createdAt`, `updatedAt`, or relationship editors. Edit uses PATCH with editable fields only so existing relationships survive. Detail/list show clean catalog fields and do not show raw relationship IDs; related read-only lists are deferred.

**Validation rules in service:** trim `name` on persist; **`name` unique per owner** (case-insensitive via `existsByUserIdAndNormalizedName`); uniqueness checked against **tag owner**, not current user (admin CRUD ajeno OK). **Inactive tags participate in uniqueness** (query does not filter `active`).

### Summary counts

| Type            | File              | Tests  | Custom vs generated                                                                               |
| --------------- | ----------------- | ------ | ------------------------------------------------------------------------------------------------- |
| Integration IT  | `TagResourceIT`   | **89** | 41 custom (ownership + uniqueness + timestamp lifecycle + delete domain) + generated CRUD/filters |
| Unit — service  | `TagServiceTest`  | **22** | All custom (ownership + uniqueness + timestamp lifecycle)                                         |
| Unit — domain   | `TagTest`         | **5**  | Generated                                                                                         |
| Unit — mapper   | `TagMapperTest`   | **1**  | Generated                                                                                         |
| Unit — DTO      | `TagDTOTest`      | **1**  | Generated                                                                                         |
| Unit — criteria | `TagCriteriaTest` | **5**  | Generated                                                                                         |
| Frontend unit   | `tag-ux.spec.tsx` | **6**  | Create/edit/detail/list UX cleanup                                                                |
| E2E             | `tag.cy.ts`       | **10** | 3 ownership + 7 CRUD/navigation                                                                   |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TagResourceIT,TagServiceTest test
```

### Integration tests — `TagResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL.

**Run:** `-Dtest=TagResourceIT`

**Test data:** `createEntity()` asigna mock user `user` (no crea user nuevo por test). Filtros con `#` en `color` usan `buildFilterRequest()` (`.param()`).

#### Ownership & authorization (16) — ✅ custom

| Test                                        | HTTP          | Actor   | Setup                                  | Expected                            |
| ------------------------------------------- | ------------- | ------- | -------------------------------------- | ----------------------------------- |
| `createTagAssignsCurrentUser`               | `POST`        | `user`  | Payload includes another user's `user` | `201`; response `user.login = user` |
| `createTagWithoutUserInPayloadSucceeds`     | `POST`        | `user`  | `user` null in DTO                     | `201`; assigned to current user     |
| `getTagOwnedByAnotherUserIsNotFound`        | `GET /:id`    | `user`  | Tag owned by other user                | `404`                               |
| `getAllTagsDoesNotIncludeAnotherUsersTags`  | `GET`         | `user`  | Other user's tag in DB                 | List excludes foreign id            |
| `getTagCountDoesNotIncludeAnotherUsersTags` | `GET /count`  | `user`  | Other user's tag in DB                 | Count `0`                           |
| `putTagOwnedByAnotherUserIsNotFound`        | `PUT /:id`    | `user`  | Tag owned by other                     | `400` (`idnotfound`)                |
| `patchTagOwnedByAnotherUserIsNotFound`      | `PATCH /:id`  | `user`  | Tag owned by other                     | `400` (`idnotfound`)                |
| `deleteTagOwnedByAnotherUserIsNotFound`     | `DELETE /:id` | `user`  | Tag owned by other                     | `404`; row still exists             |
| `updateTagCannotChangeOwner`                | `PUT /:id`    | `user`  | Own tag; DTO has other `user`          | `200`; DB owner unchanged           |
| `patchTagCannotChangeOwner`                 | `PATCH /:id`  | `user`  | Own tag; DTO has other `user`          | `200`; DB owner unchanged           |
| `adminCanGetTagOwnedByAnotherUser`          | `GET /:id`    | `admin` | Tag owned by other                     | `200`                               |
| `adminCanListAllTagsIncludingOtherUsers`    | `GET`         | `admin` | Own + other user's tags                | Both ids in list                    |
| `adminCanCountAllTagsIncludingOtherUsers`   | `GET /count`  | `admin` | 2 tags (different owners)              | Count `2`                           |
| `adminCanUpdateTagOwnedByAnotherUser`       | `PUT /:id`    | `admin` | Tag owned by other                     | `200`; name updated                 |
| `adminCanDeleteTagOwnedByAnotherUser`       | `DELETE /:id` | `admin` | Tag owned by other                     | `204`; row deleted                  |

#### Name uniqueness (5) — ✅ custom

| Test                                                                 | HTTP         | Actor                   | Setup                                                                          | Expected                             |
| -------------------------------------------------------------------- | ------------ | ----------------------- | ------------------------------------------------------------------------------ | ------------------------------------ |
| `createDuplicateTagSameUserDifferentCaseAndSpacingReturnsBadRequest` | `POST`       | `user`                  | Existing `"Comida"`; create `" COMIDA "`                                       | `400`; `error.invalid`; `params=tag` |
| `createSameTagNameForDifferentUserSucceeds`                          | `POST`       | `user` then `otherUser` | User A has `"Comida"`; user B creates `"Comida"` via `.with(user(otherLogin))` | `201`                                |
| `updateTagNameToDuplicateSameUserReturnsBadRequest`                  | `PUT /:id`   | `user`                  | Two tags same owner; rename to duplicate normalized name                       | `400`; `error.invalid`               |
| `patchTagNameToDuplicateSameUserReturnsBadRequest`                   | `PATCH /:id` | `user`                  | PATCH `name` to duplicate (case/spacing)                                       | `400`; `error.invalid`               |
| `adminEditingForeignTagCannotDuplicateWithinForeignOwner`            | `PUT /:id`   | `admin`                 | Foreign owner has `"Comida"`; admin renames other tag to `"comida"`            | `400`; uniqueness vs **tag owner**   |

**Note:** `createSameTagNameForDifferentUserSucceeds` cannot send a different `user` in the payload — service always assigns `currentUser`. The test authenticates as user B for the second `POST`.

#### Timestamp lifecycle (16) — ✅ custom/generated replacement

| Test                                        | HTTP         | Expected                                                             |
| ------------------------------------------- | ------------ | -------------------------------------------------------------------- |
| `createTagWithoutCreatedAtSucceeds`         | `POST`       | `201`; server sets `createdAt`                                       |
| `createTagWithoutUpdatedAtSucceeds`         | `POST`       | `201`; server sets `updatedAt`                                       |
| `createTagIgnoresClientTimestamps`          | `POST`       | Client timestamps ignored                                            |
| `legacyCreateTagWithNullTimestampsSucceeds` | `POST`       | Legacy generated-style null timestamps accepted on create            |
| `putExistingTag`                            | `PUT /:id`   | Same timestamps accepted; `createdAt` preserved; `updatedAt` changed |
| `putTagWithChangedCreatedAtFails`           | `PUT /:id`   | `400 invalid`                                                        |
| `putTagWithNullCreatedAtFails`              | `PUT /:id`   | `400 invalid`                                                        |
| `putTagWithChangedUpdatedAtFails`           | `PUT /:id`   | `400 invalid`                                                        |
| `putTagWithNullUpdatedAtFails`              | `PUT /:id`   | `400 invalid`                                                        |
| `partialUpdateTagWithPatch`                 | `PATCH /:id` | Omitted timestamps preserved/updated server-side                     |
| `fullUpdateTagWithPatch`                    | `PATCH /:id` | Editable fields changed; timestamps server-managed                   |
| `patchTagWithSameTimestampsSucceeds`        | `PATCH /:id` | Same timestamps accepted as no-op, `updatedAt` reset                 |
| `patchTagWithChanged/Null...Fails`          | `PATCH /:id` | Explicit changed/null timestamps rejected                            |

#### Domain rules — DELETE M2M unlink (9) — ✅ custom

| Test                                                                  | What it checks                                |
| --------------------------------------------------------------------- | --------------------------------------------- |
| `deleteUnusedTagSucceeds`                                             | Unused tag → `204`                            |
| `deleteTagUsedByFinancialTransactionUnlinksAndPreservesTransaction`   | FT keeps row; join removed                    |
| `deleteTagUsedByTransactionRuleUnlinksAndPreservesRule`               | Rule keeps row; `resultingTags` cleaned       |
| `deleteTagUsedByFinancialSubscriptionUnlinksAndPreservesSubscription` | Subscription survives                         |
| `deleteTagUsedByBudgetUnlinksAndPreservesBudget`                      | Budget survives                               |
| `deleteTagUsedInMultipleEntityTypesCleansAllRelationships`            | All four join tables cleaned                  |
| `adminDeleteForeignUsedTagSucceedsAndCleansRelationships`             | Admin `204` + cleanup on foreign owner's data |
| `updateUsedTagFieldsAllowedWhenUniquenessValid`                       | PUT/PATCH on linked tag OK                    |
| `inactiveTagStillBlocksDuplicateNormalizedName`                       | `active=false` in uniqueness query            |

**Service unit:** join-table cleanup invoked before `deleteById`; foreign delete → `false`.

#### CRUD & criteria — 🟡 JHipster generated (48)

Happy-path CRUD, required-field checks, criteria per field (`name`, `description`, `color`, `active`, timestamps, `userId`, M2M ids). Ownership filter applies on top for non-admin.

### Frontend — `tag-ux.spec.tsx` (6) — ✅ custom

| Test area     | What it checks                                                                                                   |
| ------------- | ---------------------------------------------------------------------------------------------------------------- |
| Create form   | Shows `name`, `description`, `color`, `active`; hides timestamps/user/relationship editors; active defaults true |
| Create submit | Sends editable fields only; no fake timestamps or relationships                                                  |
| Edit form     | Shows editable fields only; hides generated/relationship fields                                                  |
| Edit submit   | Uses `partialUpdateEntity`/PATCH with editable fields only                                                       |
| Detail        | Shows clean tag fields; no ID/timestamps/raw relationship IDs                                                    |
| List          | Shows catalog columns; name links to detail; no ID/timestamps/user/relationship columns                          |

### Unit — `TagServiceTest` (22) — ✅ custom

| Test                                                    | Rule under test                                                       |
| ------------------------------------------------------- | --------------------------------------------------------------------- |
| `saveShouldAssignCurrentUser`                           | `save()` sets `entity.user` from `CurrentUserService`; trims `name`   |
| `updateShouldPreserveExistingOwner`                     | `update()` keeps original owner                                       |
| `updateShouldFailWhenTagIsNotAccessible`                | `update()` throws when scoped lookup empty                            |
| `findOneShouldReturnEmptyForAnotherUsersTag`            | Non-admin scoped `findOne` → empty                                    |
| `findOneShouldUseAdminLookupWhenCurrentUserIsAdmin`     | Admin uses `findOneWithEagerRelationships`                            |
| `deleteShouldReturnFalseWhenTagIsNotAccessible`         | `delete()` → `false`, no `deleteById`                                 |
| `deleteShouldUnlinkRelationshipsBeforeDeletingTag`      | `delete()` unlinks 4 join tables then `deleteById`                    |
| `findAllWithEagerRelationshipsShouldScopeToCurrentUser` | Non-admin list uses `findAll...ByUserLogin`                           |
| `partialUpdateShouldReturnEmptyWhenTagIsNotAccessible`  | Scoped partial update → `Optional.empty()`                            |
| `saveShouldRejectDuplicateNameForSameUser`              | Duplicate normalized name for same owner → `IllegalArgumentException` |
| `updateShouldRejectDuplicateNameForOwner`               | Update to duplicate name for owner → `IllegalArgumentException`       |
| `saveShouldAllowSameNameForDifferentOwner`              | Same normalized name, different `userId` in exists check → OK         |

### E2E — `tag.cy.ts`

#### Navigation & CRUD UI (7) — ✅

| Test                                                      | What it checks                                                |
| --------------------------------------------------------- | ------------------------------------------------------------- |
| `Tags menu should load Tags page`                         | Menu → list route                                             |
| `should load create Tag page`                             | Create → form → cancel                                        |
| `detail button click should load details Tag page`        | Detail view                                                   |
| `edit button click should load edit Tag page and go back` | Edit cancel                                                   |
| `edit button click should load edit Tag page and save`    | Edit save                                                     |
| `last delete button click should delete instance of Tag`  | Delete dialog → `204`                                         |
| `should create an instance of Tag`                        | Full form → `201`; `user.login = user`; no `[data-cy="user"]` |

#### Ownership smoke (3) — ✅ custom

| Test                                                | What it checks                   |
| --------------------------------------------------- | -------------------------------- |
| `should not render user selector on create form`    | `[data-cy="user"]` absent        |
| `regular user should not see tags created by admin` | API isolation user vs admin      |
| `admin should see tags created by another user`     | Admin `GET` includes other's tag |

### Gaps

| Priority | Layer | Test                                                    | Status |
| -------- | ----- | ------------------------------------------------------- | ------ |
| Medium   | IT    | Delete guard when tag linked to transaction/rule/budget | ⏳     |
| Low      | E2E   | UI table isolation                                      | ⏳     |

---

## Category

**Ownership model:** direct `user` (required). Normal users see/edit/delete only their categories. `ROLE_ADMIN` bypasses filters.

**Domain rules:** block delete when direct children exist; leaf delete cleans references (FT/FS null, budget M2M, rule `resultingCategory` null + `active=false`); `parentCategory` immutable after create; `categoryType` mutable only when unused; child `categoryType` must match parent. Default categories on signup — **Deferred** (separate pass). See [`DOMAIN-RULES.md` §5](DOMAIN-RULES.md#5-category).

### Summary counts

| Type            | File                   | Tests   | Custom vs generated                                                                                     |
| --------------- | ---------------------- | ------- | ------------------------------------------------------------------------------------------------------- |
| Integration IT  | `CategoryResourceIT`   | **103** | 48 custom (16 ownership + 10 parent immutability + 9 uniqueness + 13 domain) + 55 JHipster CRUD/filters |
| Unit — service  | `CategoryServiceTest`  | **16**  | All custom (ownership + delete cleanup + immutability + type guards)                                    |
| Unit — domain   | `CategoryTest`         | **5**   | Generated                                                                                               |
| Unit — mapper   | `CategoryMapperTest`   | **1**   | Generated                                                                                               |
| Unit — DTO      | `CategoryDTOTest`      | **1**   | Generated                                                                                               |
| Unit — criteria | `CategoryCriteriaTest` | **5**   | Generated                                                                                               |
| E2E             | `category.cy.ts`       | **10**  | 3 ownership + 7 CRUD/navigation                                                                         |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=CategoryResourceIT,CategoryServiceTest test
```

### Integration tests — `CategoryResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL.

**Run:** `-Dtest=CategoryResourceIT`

**Test data:** `createEntity()` asigna mock user `user` (no crea user nuevo por test). Filtros con `#` en `color` usan `buildFilterRequest()` (`.param()`).

#### Ownership & authorization (16) — ✅ custom

Same matrix as Tag/FinancialAccount: create assigns current user, cross-user GET/PUT/PATCH/DELETE denied, cannot change owner, admin bypass (GET, list, count, update, delete).

| Test                                                   | HTTP          | Actor   | Setup                                  | Expected                            |
| ------------------------------------------------------ | ------------- | ------- | -------------------------------------- | ----------------------------------- |
| `createCategoryAssignsCurrentUser`                     | `POST`        | `user`  | Payload includes another user's `user` | `201`; response `user.login = user` |
| `createCategoryWithoutUserInPayloadSucceeds`           | `POST`        | `user`  | `user` null in DTO                     | `201`; assigned to current user     |
| `getCategoryOwnedByAnotherUserIsNotFound`              | `GET /:id`    | `user`  | Category owned by other user           | `404`                               |
| `getAllCategoriesDoesNotIncludeAnotherUsersCategories` | `GET`         | `user`  | Other user's category in DB            | List excludes foreign id            |
| `getCategoryCountDoesNotIncludeAnotherUsersCategories` | `GET /count`  | `user`  | Other user's category in DB            | Count `0`                           |
| `putCategoryOwnedByAnotherUserIsNotFound`              | `PUT /:id`    | `user`  | Category owned by other                | `400` (`idnotfound`)                |
| `patchCategoryOwnedByAnotherUserIsNotFound`            | `PATCH /:id`  | `user`  | Category owned by other                | `400` (`idnotfound`)                |
| `deleteCategoryOwnedByAnotherUserIsNotFound`           | `DELETE /:id` | `user`  | Category owned by other                | `404`; row still exists             |
| `updateCategoryCannotChangeOwner`                      | `PUT /:id`    | `user`  | Own category; DTO has other `user`     | `200`; DB owner unchanged           |
| `patchCategoryCannotChangeOwner`                       | `PATCH /:id`  | `user`  | Own category; DTO has other `user`     | `200`; DB owner unchanged           |
| `adminCanGetCategoryOwnedByAnotherUser`                | `GET /:id`    | `admin` | Category owned by other                | `200`                               |
| `adminCanListAllCategoriesIncludingOtherUsers`         | `GET`         | `admin` | Own + other user's categories          | Both ids in list                    |
| `adminCanCountAllCategoriesIncludingOtherUsers`        | `GET /count`  | `admin` | 2 categories (different owners)        | Count `2`                           |
| `adminCanUpdateCategoryOwnedByAnotherUser`             | `PUT /:id`    | `admin` | Category owned by other                | `200`; name updated                 |
| `adminCanDeleteCategoryOwnedByAnotherUser`             | `DELETE /:id` | `admin` | Category owned by other                | `204`; row deleted                  |

#### Parent immutability & create hierarchy (10) — ✅ custom

| Test                                                            | HTTP    | What it checks                                          |
| --------------------------------------------------------------- | ------- | ------------------------------------------------------- |
| `createCategoryWithParentOwnedByAnotherUserFails`               | `POST`  | Parent owned by other user → `400`                      |
| `createCategoryWithAccessibleParentSucceeds`                    | `POST`  | Own parent → `201`; parent persisted                    |
| `createChildWithDifferentCategoryTypeFromParentFails`           | `POST`  | Child `categoryType` ≠ parent → `400`                   |
| `updateCategoryWithSelfAsParentFails`                           | `PUT`   | Setting parent when null (self id) → `400` immutability |
| `putSettingParentCategoryFromNullFails`                         | `PUT`   | null → parent → `400`                                   |
| `putClearingParentCategoryFails`                                | `PUT`   | parent → null → `400`                                   |
| `putChangingParentCategoryFails`                                | `PUT`   | parent A → parent B → `400`                             |
| `patchChangingParentCategoryFails`                              | `PATCH` | Change parent → `400`                                   |
| `patchOmittingParentCategoryPreservesExistingParentAndSucceeds` | `PATCH` | Omit parent → preserved                                 |
| `patchSameParentCategorySucceeds`                               | `PATCH` | Same parent id → `200`                                  |

#### Sibling name uniqueness (9) — ✅ custom

| Test                                                                                  | HTTP           | Actor             | Setup                                                         | Expected                                  |
| ------------------------------------------------------------------------------------- | -------------- | ----------------- | ------------------------------------------------------------- | ----------------------------------------- |
| `createDuplicateRootCategorySameUserSameTypeDifferentCaseAndSpacingReturnsBadRequest` | `POST`         | `user`            | Root `"Comida"`; create `" COMIDA "` same type                | `400`; `error.invalid`; `params=category` |
| `createSameRootCategoryNameForDifferentUserSucceeds`                                  | `POST`         | `user` then other | Same root name, different owners                              | `201`                                     |
| `createSameRootCategoryNameSameUserDifferentCategoryTypeSucceeds`                     | `POST`         | `user`            | Same root name, `EXPENSE` vs `INCOME`                         | `201`                                     |
| `createDuplicateChildCategoryUnderSameParentReturnsBadRequest`                        | `POST`         | `user`            | Sibling `"Comida"`; create `" comida "` under same parent     | `400`                                     |
| `createSameChildCategoryNameUnderDifferentParentSucceeds`                             | `POST`         | `user`            | Same child name under different parents                       | `201`                                     |
| `patchCategoryTypeIntoScopeWhereSiblingNameExistsReturnsBadRequest`                   | `PATCH`        | `user`            | Change `categoryType` into scope with same normalized name    | `400`                                     |
| `adminEditingForeignCategoryCannotDuplicateWithinForeignOwnerSiblingScope`            | `PUT`          | `admin`           | Admin renames foreign sibling to duplicate within owner scope | `400`                                     |
| `createAndUpdateCategoryNameTrimPersists`                                             | `POST` + `PUT` | `user`            | `" Comida "` / `" Viajes "` trimmed on persist                | `201`/`200`; trimmed names                |
| `inactiveCategoryStillBlocksDuplicateNormalizedSiblingName`                           | `POST`         | `user`            | Inactive `"Comida"` blocks `" comida "`                       | `400`                                     |

**Scope:** duplicate = same owner + same `categoryType` + same `parentCategory` (both null for roots) + same normalized `name`. Inactive categories participate.

#### Timestamp lifecycle — ✅ custom

`createdAt` / `updatedAt` are server-owned. Create accepts missing timestamps and ignores client-provided values. PUT/PATCH preserve `createdAt`; explicit null or changed `createdAt`/`updatedAt` returns `400 invalid`; successful PUT/PATCH sets `updatedAt = now`.

| Test                                      | HTTP  | What it checks                                          |
| ----------------------------------------- | ----- | ------------------------------------------------------- |
| `createCategoryWithoutCreatedAtSucceeds`  | POST  | Missing `createdAt` accepted; server sets timestamp     |
| `createCategoryWithoutUpdatedAtSucceeds`  | POST  | Missing `updatedAt` accepted; server sets timestamp     |
| `createCategoryIgnoresClientTimestamps`   | POST  | Client timestamps ignored                               |
| `putCategoryWithChangedCreatedAtFails`    | PUT   | Changed `createdAt` → `400`                             |
| `putCategoryWithChangedUpdatedAtFails`    | PUT   | Changed `updatedAt` → `400`                             |
| `putCategoryWithNullCreatedAtFails`       | PUT   | Explicit null `createdAt` → `400`                       |
| `putCategoryWithNullUpdatedAtFails`       | PUT   | Explicit null `updatedAt` → `400`                       |
| `patchCategoryWithSameTimestampsSucceeds` | PATCH | Same timestamps allowed as no-op before `updatedAt=now` |
| `patchCategoryWithChangedCreatedAtFails`  | PATCH | Changed `createdAt` → `400`                             |
| `patchCategoryWithChangedUpdatedAtFails`  | PATCH | Changed `updatedAt` → `400`                             |
| `patchCategoryWithNullCreatedAtFails`     | PATCH | Explicit null `createdAt` → `400`                       |
| `patchCategoryWithNullUpdatedAtFails`     | PATCH | Explicit null `updatedAt` → `400`                       |

#### Domain rules — DELETE cleanup & type guards (13) — ✅ custom

| Test                                                                                     | What it checks                                 |
| ---------------------------------------------------------------------------------------- | ---------------------------------------------- |
| `deleteCategoryWithDirectActiveChildFails`                                               | Active child → `400`                           |
| `deleteCategoryWithDirectInactiveChildFails`                                             | Inactive child → `400`                         |
| `deleteUnusedLeafCategorySucceeds`                                                       | Leaf no refs → `204`                           |
| `deleteLeafCategoryUsedByFinancialTransactionUnlinksAndPreservesTransaction`             | FT `category` null                             |
| `deleteLeafCategoryUsedByFinancialSubscriptionUnlinksAndPreservesSubscription`           | FS `category` null                             |
| `deleteLeafCategoryUsedByBudgetUnlinksAndPreservesBudget`                                | Budget M2M cleaned                             |
| `deleteLeafCategoryUsedByTransactionRuleUnlinksAndDeactivatesRule`                       | Rule `resultingCategory` null + `active=false` |
| `deleteLeafCategoryUsedInMultipleEntityTypesCleansAllReferences`                         | All four cleanups                              |
| `adminDeleteForeignUsedLeafCategorySucceedsAndCleansReferences`                          | Admin `204` + cleanup                          |
| `changeCategoryTypeOfCategoryWithChildrenFails`                                          | Children block type change                     |
| `changeCategoryTypeOfCategoryUsedByFinancialTransactionFails`                            | FT ref blocks type change                      |
| `changeCategoryTypeOfUnusedLeafCategorySucceedsWhenUniquenessAndParentCompatibilityHold` | Unused leaf OK                                 |
| `renameUsedCategorySucceedsWhenUniquenessPreserved`                                      | Used leaf rename OK                            |

**Service unit:** includes ownership/delete/parent/type/name uniqueness plus timestamp lifecycle tests: create sets/ignores timestamps; update rejects null/changed timestamps and sets `updatedAt=now`; patch preserves `createdAt`, sets `updatedAt=now`, allows same timestamps, and rejects explicit null/changed timestamps.

**Deprecated/replaced:** `updateCategoryCreatingCycleFails`, `patchCategoryIntoParentWhereSiblingNameExistsReturnsBadRequest`, `partialUpdateShouldRejectDuplicateWhenParentChangesIntoSiblingScope` (move no longer allowed).

#### CRUD & criteria — 🟡 JHipster generated (55)

Happy-path CRUD, required-field checks, criteria per field (`name`, `description`, `categoryType`, `color`, `icon`, `active`, timestamps, `userId`, `parentCategoryId`, M2M ids). Timestamp required-field checks are replaced by server-owned timestamp lifecycle tests. Ownership filter applies on top for non-admin.

### Unit — `CategoryServiceTest` — ✅ custom

| Test                                                        | Rule under test                                       |
| ----------------------------------------------------------- | ----------------------------------------------------- |
| `saveShouldAssignCurrentUser`                               | `save()` sets `entity.user` from `CurrentUserService` |
| `updateShouldPreserveExistingOwner`                         | `update()` keeps original owner                       |
| `updateShouldFailWhenCategoryIsNotAccessible`               | `update()` throws when scoped lookup empty            |
| `findOneShouldReturnEmptyForAnotherUsersCategory`           | Non-admin scoped `findOne` → empty                    |
| `findOneShouldUseAdminLookupWhenCurrentUserIsAdmin`         | Admin uses `findOneWithEagerRelationships`            |
| `deleteShouldReturnFalseWhenCategoryIsNotAccessible`        | `delete()` → `false`, no `deleteById`                 |
| `deleteShouldRejectWhenCategoryHasChildren`                 | `delete()` throws when children exist                 |
| `deleteShouldUnlinkRelationshipsBeforeDeletingCategory`     | Cleanup then `deleteById`                             |
| `findAllWithEagerRelationshipsShouldScopeToCurrentUser`     | Non-admin list uses `findAll...ByUserLogin`           |
| `partialUpdateShouldReturnEmptyWhenCategoryIsNotAccessible` | Scoped partial update → `Optional.empty()`            |
| `updateShouldRejectParentCategoryChange`                    | PUT parent change → `IllegalArgumentException`        |
| `partialUpdateShouldRejectParentCategoryChange`             | PATCH parent change → `IllegalArgumentException`      |
| `saveShouldRejectDuplicateSiblingName`                      | Duplicate sibling name → `IllegalArgumentException`   |
| `saveShouldAllowSameNameUnderDifferentParent`               | Same name under different parent → OK                 |
| `saveShouldRejectChildCategoryTypeMismatchWithParent`       | Child type ≠ parent on create                         |
| `updateShouldRejectCategoryTypeChangeWhenCategoryIsInUse`   | Type change when FT reference exists                  |

### E2E — `category.cy.ts`

#### Navigation & CRUD UI (7) — ✅

| Test                                                           | What it checks                                                |
| -------------------------------------------------------------- | ------------------------------------------------------------- |
| `Categories menu should load Categories page`                  | Menu → list route                                             |
| `should load create Category page`                             | Create → form → cancel                                        |
| `detail button click should load details Category page`        | Detail view                                                   |
| `edit button click should load edit Category page and go back` | Edit cancel                                                   |
| `edit button click should load edit Category page and save`    | Edit save                                                     |
| `last delete button click should delete instance of Category`  | Delete dialog → `204`                                         |
| `should create an instance of Category`                        | Full form → `201`; `user.login = user`; no `[data-cy="user"]` |

#### Ownership smoke (3) — ✅ custom

| Test                                                      | What it checks                        |
| --------------------------------------------------------- | ------------------------------------- |
| `should not render user selector on create form`          | `[data-cy="user"]` absent             |
| `regular user should not see categories created by admin` | API isolation user vs admin           |
| `admin should see categories created by another user`     | Admin `GET` includes other's category |

### Gaps

| Priority | Layer | Test                                                 | Status |
| -------- | ----- | ---------------------------------------------------- | ------ |
| Medium   | IT    | Delete guard: children, transactions, rules, budgets | ⏳     |
| Medium   | IT    | `categoryType` rules vs parent                       | ⏳     |
| Low      | E2E   | UI table isolation                                   | ⏳     |

---

## Budget

**Ownership model:** direct `user` (required). Normal users see/edit/delete only their budgets. `ROLE_ADMIN` bypasses filters.

**Domain rules in service:** assign `user` on create; ignore client `user`; preserve owner on update/patch; scope all reads/writes; M2M resolved vs **budget owner** (`ownerLogin`); explicit DELETE cleanup on 3 join tables; `amount` > 0; date range; account currency match; expense/BOTH categories only; empty M2M = reporting semantics (documented).

**Validation — PATCH:** JsonNode link semantics: M2M field **absent** preserves; **present** with `null` or `[]` clears; **present** with ids replaces via `resolve*`. Foreign id → `400 invalid` (`params=budget`).

### Summary counts

| Type            | File                 | Tests   | Custom vs generated                                                                                  |
| --------------- | -------------------- | ------- | ---------------------------------------------------------------------------------------------------- |
| Integration IT  | `BudgetResourceIT`   | **125** | 46 custom (16 ownership + 4 M2M create + 11 PATCH link + 15 domain rules) + 79 JHipster CRUD/filters |
| Unit — service  | `BudgetServiceTest`  | **17**  | All custom (ownership + M2M + patchNode + domain validations + delete cleanup)                       |
| Unit — domain   | `BudgetTest`         | **4**   | Generated                                                                                            |
| Unit — mapper   | `BudgetMapperTest`   | **1**   | Generated                                                                                            |
| Unit — DTO      | `BudgetDTOTest`      | **1**   | Generated                                                                                            |
| Unit — criteria | `BudgetCriteriaTest` | **5**   | Generated                                                                                            |
| E2E             | `budget.cy.ts`       | **10**  | 3 ownership + 7 CRUD/navigation                                                                      |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=BudgetResourceIT,BudgetServiceTest test
```

### Integration tests — `BudgetResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL.

**Run:** `-Dtest=BudgetResourceIT`

**Test data:** `createEntity()` asigna mock user `user` (no crea user nuevo por test).

#### Ownership & authorization (16) — ✅ custom

Same matrix as Tag/Category/FinancialAccount: create assigns current user, cross-user GET/PUT/PATCH/DELETE denied, cannot change owner, admin bypass (GET, list, count, update, delete).

| Test                                              | HTTP          | Actor   | Setup                                     | Expected                            |
| ------------------------------------------------- | ------------- | ------- | ----------------------------------------- | ----------------------------------- |
| `createBudgetAssignsCurrentUser`                  | `POST`        | `user`  | Payload includes another user's `user`    | `201`; response `user.login = user` |
| `createBudgetWithoutUserInPayloadSucceeds`        | `POST`        | `user`  | `user` null in DTO                        | `201`; assigned to current user     |
| `getBudgetOwnedByAnotherUserIsNotFound`           | `GET /:id`    | `user`  | Budget owned by other user                | `404`                               |
| `getAllBudgetsDoesNotIncludeAnotherUsersBudgets`  | `GET`         | `user`  | Other user's budget in DB                 | List excludes foreign id            |
| `getBudgetCountDoesNotIncludeAnotherUsersBudgets` | `GET /count`  | `user`  | Other user's budget in DB                 | Count `0`                           |
| `putBudgetOwnedByAnotherUserIsNotFound`           | `PUT /:id`    | `user`  | Budget owned by other                     | `400` (`idnotfound`)                |
| `patchBudgetOwnedByAnotherUserIsNotFound`         | `PATCH /:id`  | `user`  | Budget owned by other                     | `400` (`idnotfound`)                |
| `deleteBudgetOwnedByAnotherUserIsNotFound`        | `DELETE /:id` | `user`  | Budget owned by other                     | `404`; row still exists             |
| `updateBudgetCannotChangeOwner`                   | `PUT /:id`    | `user`  | Own budget; DTO has other `user`          | `200`; DB owner unchanged           |
| `patchBudgetCannotChangeOwner`                    | `PATCH /:id`  | `user`  | Minimal JSON with `name` + foreign `user` | `200`; DB owner unchanged           |
| `adminCanGetBudgetOwnedByAnotherUser`             | `GET /:id`    | `admin` | Budget owned by other                     | `200`                               |
| `adminCanListAllBudgetsIncludingOtherUsers`       | `GET`         | `admin` | Own + other user's budgets                | Both ids in list                    |
| `adminCanCountAllBudgetsIncludingOtherUsers`      | `GET /count`  | `admin` | 2 budgets (different owners)              | Count `2`                           |
| `adminCanUpdateBudgetOwnedByAnotherUser`          | `PUT /:id`    | `admin` | Budget owned by other                     | `200`; name updated                 |
| `adminCanDeleteBudgetOwnedByAnotherUser`          | `DELETE /:id` | `admin` | Budget owned by other                     | `204`; row deleted                  |

#### M2M link rules (4) — ✅ custom

| Test                                              | HTTP   | What it checks                             |
| ------------------------------------------------- | ------ | ------------------------------------------ |
| `createBudgetWithAccountOwnedByAnotherUserFails`  | `POST` | Foreign `accounts` id → `400`              |
| `createBudgetWithCategoryOwnedByAnotherUserFails` | `POST` | Foreign `categories` id → `400`            |
| `createBudgetWithTagOwnedByAnotherUserFails`      | `POST` | Foreign `tags` id → `400`                  |
| `createBudgetWithAccessibleAccountSucceeds`       | `POST` | Own account in M2M → `201`; link persisted |

#### PATCH JsonNode link semantics (11) — ✅ custom

| Test                                         | Body                  | Expected                                |
| -------------------------------------------- | --------------------- | --------------------------------------- |
| `patchBudgetScalarPreservesM2mLinks`         | `{"name":"…"}` only   | `200`; accounts/tags preserved          |
| `patchBudgetNullTagsClearsTags`              | `{"tags":null}`       | `200`; tags empty                       |
| `patchBudgetNullAccountsClearsAccounts`      | `{"accounts":null}`   | `200`; accounts empty                   |
| `patchBudgetNullCategoriesClearsCategories`  | `{"categories":null}` | `200`; categories empty                 |
| `patchBudgetEmptyTagsClearsTags`             | `{"tags":[]}`         | `200`; tags empty                       |
| `patchBudgetEmptyAccountsClearsAccounts`     | `{"accounts":[]}`     | `200`; accounts empty                   |
| `patchBudgetEmptyCategoriesClearsCategories` | `{"categories":[]}`   | `200`; categories empty                 |
| `patchBudgetReplacesTags`                    | `{"tags":[{"id":B}]}` | `200`; only tag B                       |
| `patchBudgetForeignAccountFails`             | foreign account id    | `400`; `error.invalid`; `params=budget` |
| `patchBudgetForeignCategoryFails`            | foreign category id   | `400`                                   |
| `patchBudgetForeignTagFails`                 | foreign tag id        | `400`                                   |

#### Domain rules (15) — ✅ custom

| Test                                                                  | Rule under test                                         |
| --------------------------------------------------------------------- | ------------------------------------------------------- |
| `deleteBudgetWithM2mLinksCleansJoinTablesAndPreservesRelatedEntities` | DELETE cleans 3 join tables; FA/Category/Tag survive    |
| `adminDeleteForeignBudgetWithM2mLinksSucceeds`                        | Admin DELETE foreign budget with links → `204`          |
| `adminUpdateForeignBudgetWithOwnerValidLinksSucceeds`                 | Admin PUT owner-valid category → `200`                  |
| `adminUpdateForeignBudgetWithAdminOwnedCategoryFails`                 | Admin-owned category on foreign budget → `400`          |
| `createBudgetWithZeroAmountFails`                                     | `amount = 0` → `400`                                    |
| `createBudgetWithNegativeAmountFails`                                 | `amount < 0` → `400`                                    |
| `createBudgetWithEndDateBeforeStartDateFails`                         | `endDate < startDate` → `400`                           |
| `createBudgetWithAccountCurrencyMismatchFails`                        | Linked account currency ≠ budget → `400`                |
| `updateBudgetCurrencyWithLinkedAccountMismatchFails`                  | Currency change with mismatched linked accounts → `400` |
| `patchBudgetCurrencyWithoutLinkedAccountsSucceeds`                    | Currency change with empty accounts → `200`             |
| `createBudgetWithExpenseCategorySucceeds`                             | `EXPENSE` category → `201`                              |
| `createBudgetWithBothCategorySucceeds`                                | `BOTH` category → `201`                                 |
| `createBudgetWithIncomeCategoryFails`                                 | `INCOME` category → `400`                               |
| `patchBudgetStatusPausedPreservesM2mLinks`                            | `PAUSED` keeps M2M                                      |
| `patchBudgetStatusCompletedPreservesM2mLinks`                         | `COMPLETED` keeps M2M                                   |

**PATCH note:** Successful scalar PATCH tests use `ObjectNode` minimal JSON (no M2M keys). JHipster `partialUpdateBudgetWithPatch` / `fullUpdateBudgetWithPatch` updated accordingly.

#### CRUD & criteria — 🟡 JHipster generated (79)

Happy-path CRUD, required-field checks, criteria per field (`name`, `amount`, `currency`, `period`, dates, `status`, `tagMatchMode`, `warningPercentage`, timestamps, `userId`, `accountsId`, `categoriesId`, `tagsId`). Ownership filter applies on top for non-admin.

### Unit — `BudgetServiceTest` (17) — ✅ custom

| Test                                                             | Rule under test                                        |
| ---------------------------------------------------------------- | ------------------------------------------------------ |
| `saveShouldAssignCurrentUser`                                    | `save()` sets `entity.user` from `CurrentUserService`  |
| `updateShouldPreserveExistingOwner`                              | `update()` keeps original owner                        |
| `updateShouldFailWhenBudgetIsNotAccessible`                      | `update()` throws when scoped lookup empty             |
| `findOneShouldReturnEmptyForAnotherUsersBudget`                  | Non-admin scoped `findOne` → empty                     |
| `findOneShouldUseAdminLookupWhenCurrentUserIsAdmin`              | Admin uses `findOneWithEagerRelationships`             |
| `deleteShouldReturnFalseWhenBudgetIsNotAccessible`               | `delete()` → `false`, no cleanup/delete                |
| `deleteShouldCleanupRelationshipsBeforeRemovingAccessibleBudget` | Cleanup queries before `deleteById`                    |
| `findAllWithEagerRelationshipsShouldScopeToCurrentUser`          | Non-admin list uses `findAll...ByUserLogin`            |
| `partialUpdateShouldReturnEmptyWhenBudgetIsNotAccessible`        | Scoped partial update → `Optional.empty()`             |
| `saveShouldRejectInaccessibleAccount`                            | Foreign account on create → `IllegalArgumentException` |
| `updateShouldResolveOwnerAccount`                                | PUT resolves owner account into M2M                    |
| `partialUpdateWithPatchNodeAbsentPreservesLinks`                 | No `has("accounts")` → links unchanged                 |
| `partialUpdateWithPatchNodeNullTagsClearsTags`                   | `has("tags")` + null → empty set                       |
| `partialUpdateWithPatchNodeReplacesAccounts`                     | `has("accounts")` + ids → replace                      |
| `saveShouldRejectZeroAmount`                                     | `amount = 0` → `IllegalArgumentException`              |
| `saveShouldRejectAccountCurrencyMismatch`                        | Account currency ≠ budget → `IllegalArgumentException` |
| `saveShouldRejectEndDateBeforeStartDate`                         | `endDate < startDate` → `IllegalArgumentException`     |

### E2E — `budget.cy.ts`

#### Navigation & CRUD UI (7) — ✅

| Test                                                         | What it checks                                                |
| ------------------------------------------------------------ | ------------------------------------------------------------- |
| `Budgets menu should load Budgets page`                      | Menu → list route                                             |
| `should load create Budget page`                             | Create → form → cancel                                        |
| `detail button click should load details Budget page`        | Detail view                                                   |
| `edit button click should load edit Budget page and go back` | Edit cancel                                                   |
| `edit button click should load edit Budget page and save`    | Edit save                                                     |
| `last delete button click should delete instance of Budget`  | Delete dialog → `204`                                         |
| `should create an instance of Budget`                        | Full form → `201`; `user.login = user`; no `[data-cy="user"]` |

#### Ownership smoke (3) — ✅ custom

| Test                                                   | What it checks                      |
| ------------------------------------------------------ | ----------------------------------- |
| `should not render user selector on create form`       | `[data-cy="user"]` absent           |
| `regular user should not see budgets created by admin` | API isolation user vs admin         |
| `admin should see budgets created by another user`     | Admin `GET` includes other's budget |

### Gaps

| Priority | Layer | Test                                                                        | Status |
| -------- | ----- | --------------------------------------------------------------------------- | ------ |
| Medium   | IT    | Empty M2M semantics (all accounts in currency, any category, no tag filter) | ⏳     |
| Low      | E2E   | UI table isolation                                                          | ⏳     |

---

## FinancialSubscription

**Ownership model:** direct `user` (required). Normal users see/edit/delete only their subscriptions. `ROLE_ADMIN` bypasses filters.

**Domain rules in service:** assign `user` on create; ignore client `user`; preserve owner on update/patch; scope all reads/writes; optional `account` / `category` / `tags` must be owned by **subscription owner** when present. PATCH uses raw `JsonNode` to distinguish absent vs explicit null/empty link fields. DELETE unlinks FT and disables rules before row delete. `status` PAUSED/CANCELLED preserves links. Date and structural guards in service.

### Summary counts

| Type            | File                                | Tests   | Custom vs generated                                                        |
| --------------- | ----------------------------------- | ------- | -------------------------------------------------------------------------- |
| Integration IT  | `FinancialSubscriptionResourceIT`   | **138** | 35 custom (16 ownership + 9 links + 10 domain) + 103 JHipster CRUD/filters |
| Unit — service  | `FinancialSubscriptionServiceTest`  | **15**  | All custom (ownership + links + domain)                                    |
| Unit — domain   | `FinancialSubscriptionTest`         | **6**   | Generated                                                                  |
| Unit — mapper   | `FinancialSubscriptionMapperTest`   | **1**   | Generated                                                                  |
| Unit — DTO      | `FinancialSubscriptionDTOTest`      | **1**   | Generated                                                                  |
| Unit — criteria | `FinancialSubscriptionCriteriaTest` | **5**   | Generated                                                                  |
| E2E             | `financial-subscription.cy.ts`      | **10**  | 3 ownership + 7 CRUD/navigation                                            |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FinancialSubscriptionResourceIT,FinancialSubscriptionServiceTest test
```

### Integration tests — `FinancialSubscriptionResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL.

**Run:** `-Dtest=FinancialSubscriptionResourceIT`

**Test data:** `createEntity()` asigna mock user `user` (no crea user nuevo por test).

#### Ownership & authorization (16) — ✅ custom

Same matrix as Tag/Category/Budget/FinancialAccount.

#### Link rules — create (4) — ✅ custom

| Test                                                             | HTTP   | What it checks                      |
| ---------------------------------------------------------------- | ------ | ----------------------------------- |
| `createFinancialSubscriptionWithAccountOwnedByAnotherUserFails`  | `POST` | Foreign `account` id → `400`        |
| `createFinancialSubscriptionWithCategoryOwnedByAnotherUserFails` | `POST` | Foreign `category` id → `400`       |
| `createFinancialSubscriptionWithTagOwnedByAnotherUserFails`      | `POST` | Foreign `tags` id → `400`           |
| `createFinancialSubscriptionWithAccessibleAccountSucceeds`       | `POST` | Own account → `201`; link persisted |

#### Link rules — PATCH / PUT (5) — ✅ custom

PATCH bodies use raw JSON so field presence is explicit (`application/merge-patch+json`).

| Test                                                                    | HTTP    | What it checks                          |
| ----------------------------------------------------------------------- | ------- | --------------------------------------- |
| `patchFinancialSubscriptionWithoutAccountFieldPreservesExistingAccount` | `PATCH` | Omit `account` → existing account kept  |
| `patchFinancialSubscriptionWithNullAccountClearsAccount`                | `PATCH` | `"account": null` → account cleared     |
| `patchFinancialSubscriptionWithEmptyTagsClearsTags`                     | `PATCH` | `"tags": []` → tags cleared             |
| `updateFinancialSubscriptionWithForeignCategoryOrAccountFails`          | `PUT`   | Foreign `account` or `category` → `400` |
| `patchFinancialSubscriptionWithForeignTagFails`                         | `PATCH` | Foreign `tags` id → `400`               |

#### Domain rules — DELETE / UPDATE (10) — ✅ custom

| Test                                                                                  | HTTP     | What it checks                              |
| ------------------------------------------------------------------------------------- | -------- | ------------------------------------------- |
| `deleteFinancialSubscriptionUsedByFinancialTransactionUnlinksAndPreservesTransaction` | `DELETE` | FT survives; `financialSubscription = null` |
| `deleteFinancialSubscriptionUsedByTransactionRuleUnlinksAndDeactivatesRule`           | `DELETE` | Rule output null + `active=false`           |
| `deleteFinancialSubscriptionUsedInMultipleEntityTypesCleansAllReferences`             | `DELETE` | FT + rule cleaned                           |
| `adminDeleteForeignUsedFinancialSubscriptionSucceedsAndCleansReferences`              | `DELETE` | Admin `204` + cleanup                       |
| `patchFinancialSubscriptionStatusCancelledSucceeds`                                   | `PATCH`  | `status=CANCELLED` persisted                |
| `patchFinancialSubscriptionStatusCancelledKeepsTransactionLinked`                     | `PATCH`  | `status=CANCELLED`; FT link preserved       |
| `createFinancialSubscriptionWithEndDateBeforeStartDateFails`                          | `POST`   | Date validation → `400`                     |
| `updateFinancialSubscriptionCurrencyWithLinkedTransactionFails`                       | `PUT`    | Structural guard → `400`                    |
| `createFinancialSubscriptionWithAccountCurrencyMismatchFails`                         | `POST`   | `account.currency` != subscription → `400`  |
| `adminUpdateForeignFinancialSubscriptionWithForeignCategoryFails`                     | `PUT`    | Link vs subscription owner → `400`          |

#### CRUD & criteria — 🟡 JHipster generated (103)

Happy-path CRUD, required-field checks, criteria per field. Ownership filter applies on top for non-admin.

### Unit — `FinancialSubscriptionServiceTest` (15) — ✅ custom

Ownership matrix (save/update/findOne/delete/list/partial) + link validation + date/structural/currency guards + delete cleanup.

### E2E — `financial-subscription.cy.ts`

#### Navigation & CRUD UI (7) — ✅

Rehabilitated create/delete (sin selector User). API payload sin `user`.

#### Ownership smoke (3) — ✅ custom

| Test                                                                   | What it checks                            |
| ---------------------------------------------------------------------- | ----------------------------------------- |
| `should not render user selector on create form`                       | `[data-cy="user"]` absent                 |
| `regular user should not see financial subscriptions created by admin` | API isolation                             |
| `admin should see financial subscriptions created by another user`     | Admin `GET` includes other's subscription |

### Gaps

| Priority | Layer | Test                                                      | Status |
| -------- | ----- | --------------------------------------------------------- | ------ |
| Medium   | IT    | Delete guard when linked to transactions / rules          | ⏳     |
| Medium   | IT    | FT create with another user's subscription → `400` (HTTP) | ⏳     |
| Low      | E2E   | UI table isolation                                        | ⏳     |

---

## TransactionRule

**Ownership model:** direct `user` (required). Normal users see/edit/delete only their rules. `ROLE_ADMIN` bypasses filters on the rule itself.

**Link rule:** `resultingCategory` / `resultingFinancialSubscription` / `resultingTags` must belong to the **rule owner** — even when admin edits another user's rule.

**Domain rules in service:** assign `user` on create; ignore client `user`; preserve owner on update/patch; scope all reads/writes; normalize name/descriptions; enforce normalized per-owner name uniqueness; require at least one output; validate owner-scoped outputs; strict server-owned `createdAt` / `updatedAt`; PUT is a full DTO update; PATCH uses `JsonNode` for output link field presence; delete cleans child conditions + resultingTags join rows.

### Summary counts

| Type            | File                           | Tests   | Custom vs generated                                                                                                               |
| --------------- | ------------------------------ | ------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Integration IT  | `TransactionRuleResourceIT`    | **109** | Ownership/link/domain/API ergonomics custom tests + JHipster CRUD/filters                                                         |
| Unit — service  | `TransactionRuleServiceTest`   | **14**  | All custom (ownership, owner-scoped links, cleanup, timestamp guards)                                                             |
| Unit — domain   | `TransactionRuleTest`          | **6**   | Generated                                                                                                                         |
| Unit — mapper   | `TransactionRuleMapperTest`    | **1**   | Generated                                                                                                                         |
| Unit — DTO      | `TransactionRuleDTOTest`       | **1**   | Generated                                                                                                                         |
| Unit — criteria | `TransactionRuleCriteriaTest`  | **5**   | Generated                                                                                                                         |
| Frontend UX     | `transaction-rule-ux.spec.tsx` | **20**  | Product-oriented list/detail summaries, create inactive flow, edit hydration, grouped edit form, detail embedded condition editor |
| E2E             | `transaction-rule.cy.ts`       | **10**  | 3 ownership + 7 CRUD/navigation                                                                                                   |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TransactionRuleResourceIT,TransactionRuleServiceTest test
```

### Integration tests — `TransactionRuleResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL.

**Test data:** `createEntity()` asigna mock user `user`.

#### Ownership & authorization (16) — ✅ custom

Same matrix as Tag/Category/Budget/FinancialSubscription.

#### Link rules — create (4) — ✅ custom

| Test                                                           | HTTP   | What it checks                                   |
| -------------------------------------------------------------- | ------ | ------------------------------------------------ |
| `createTransactionRuleWithCategoryOwnedByAnotherUserFails`     | `POST` | Foreign `resultingCategory` → `400`              |
| `createTransactionRuleWithSubscriptionOwnedByAnotherUserFails` | `POST` | Foreign `resultingFinancialSubscription` → `400` |
| `createTransactionRuleWithTagOwnedByAnotherUserFails`          | `POST` | Foreign `resultingTags` → `400`                  |
| `createTransactionRuleWithAccessibleCategorySucceeds`          | `POST` | Own category → `201`                             |

#### Link rules — PATCH / PUT (5) — ✅ custom

| Test                                                                | HTTP    | What it checks                        |
| ------------------------------------------------------------------- | ------- | ------------------------------------- |
| `patchTransactionRuleWithoutCategoryFieldPreservesExistingCategory` | `PATCH` | Omit `resultingCategory` → preserved  |
| `patchTransactionRuleWithNullCategoryClearsCategory`                | `PATCH` | `"resultingCategory": null` → cleared |
| `patchTransactionRuleWithEmptyTagsClearsTags`                       | `PATCH` | `"resultingTags": []` → cleared       |
| `updateTransactionRuleWithForeignCategoryOrSubscriptionFails`       | `PUT`   | Foreign category/subscription → `400` |
| `patchTransactionRuleWithForeignTagFails`                           | `PATCH` | Foreign tag → `400`                   |

#### Owner-scoped outputs — admin (1) — ✅ custom

| Test                                                      | HTTP  | What it checks                                           |
| --------------------------------------------------------- | ----- | -------------------------------------------------------- |
| `adminCannotAttachCurrentUsersCategoryToAnotherUsersRule` | `PUT` | Admin cannot mix rule owner with another user's category |

#### Parent-centered conditions endpoint (5) — ✅ custom

| Test                                                                     | HTTP  | What it checks                                                                |
| ------------------------------------------------------------------------ | ----- | ----------------------------------------------------------------------------- |
| `getTransactionRuleConditionsReturnsOwnConditionsSorted`                 | `GET` | Own parent returns related conditions sorted by `position ASC`, then `id ASC` |
| `getTransactionRuleConditionsReturnsEmptyListWhenOwnRuleHasNoConditions` | `GET` | Own parent with no conditions returns `200 []`                                |
| `getTransactionRuleConditionsOwnedByAnotherUserIsNotFound`               | `GET` | Normal user foreign parent → `404`                                            |
| `adminCanGetTransactionRuleConditionsOwnedByAnotherUser`                 | `GET` | Admin can read conditions for foreign parent                                  |
| `getTransactionRuleConditionsOnlyReturnsRequestedRuleConditions`         | `GET` | Endpoint returns only conditions belonging to requested rule                  |

#### Frontend UX — `transaction-rule-ux.spec.tsx` — ✅

| Area                       | What it checks                                                                                                                                           |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Product list               | List hides generated ID/created columns, shows Name/Status/Priority/Conditions/Result/Updated/Actions                                                    |
| Compact detail             | Detail renders Identity, Matching logic, Result, Status / Metadata, and embedded Conditions; no document-like When/Then headings                         |
| Create flow                | Create hides timestamps/active/embedded conditions, shows Save and add conditions, submits `active=false`, and redirects create success to detail        |
| Create/edit shell          | Create is parent-only; edit shows Active and Manage conditions link                                                                                      |
| Edit hydration             | Edit waits for the requested entity and hydrates scalar fields, category, subscription, multi-select tags, and active                                    |
| Edit general fields        | Edit is grouped into identity/matching/result/status; `ValidatedField`s remain direct form children; condition controls are not embedded                 |
| Embedded detail collection | Detail loads conditions from `GET /api/transaction-rules/{id}/conditions`; empty state is non-breaking                                                   |
| Active toggle UX           | Disabled when conditions are empty or unavailable; enabled when at least one condition is loaded                                                         |
| Add condition              | Detail opens embedded smart form without parent selector; POST includes `transactionRule: { id: currentRule }`; frontend does not auto-activate the rule |
| Edit condition             | Detail opens embedded smart form without parent selector; PATCH sends editable fields only, no reparenting                                               |
| Delete condition           | Detail confirms, DELETEs condition, refreshes list; delete failure shows an inline error                                                                 |
| Embedded table actions     | No View action; Edit/Delete remain because condition values are already visible in the table                                                             |

#### CRUD domain lifecycle — ✅ custom

| Test                                                         | HTTP     | What it checks                                                                          |
| ------------------------------------------------------------ | -------- | --------------------------------------------------------------------------------------- |
| `createTransactionRuleWithoutCreatedAtUsesServerTimestamp`   | `POST`   | Server owns `createdAt`                                                                 |
| `createTransactionRuleWithoutUpdatedAtUsesServerTimestamp`   | `POST`   | Server owns `updatedAt`                                                                 |
| `createTransactionRuleNormalizesTextFields`                  | `POST`   | Trim name/description/resultingDescription                                              |
| `createTransactionRuleWithBlankNameFails`                    | `POST`   | Name blank after trim → `400`                                                           |
| `createTransactionRuleWithDuplicateNameForSameOwnerFails`    | `POST`   | Name unique per owner, case/trim-insensitive                                            |
| `createTransactionRuleWithSameNameForDifferentOwnerSucceeds` | `POST`   | Name uniqueness owner-scoped                                                            |
| `createTransactionRuleWithNoOutputsFails`                    | `POST`   | At least one output required                                                            |
| `createActiveTransactionRuleWithZeroConditionsFails`         | `POST`   | Active rule requires conditions                                                         |
| `patchRequiredFieldsWithNullFails`                           | `PATCH`  | Required scalar nulls rejected                                                          |
| `patchChangingCreatedAtFails`                                | `PATCH`  | `createdAt` immutable                                                                   |
| `putChangingCreatedAtFails` / `putNullCreatedAtFails`        | `PUT`    | `createdAt` explicit changed/null → `400`                                               |
| `putChangingUpdatedAtFails` / `putNullUpdatedAtFails`        | `PUT`    | `updatedAt` explicit changed/null → `400`                                               |
| `patchChangingUpdatedAtFails`                                | `PATCH`  | `updatedAt` explicit changed → `400`; explicit null covered by required-field null test |
| `partialUpdateTransactionRuleWithPatch`                      | `PATCH`  | Omitting `updatedAt` succeeds and server sets `updatedAt=now`                           |
| `patchActiveTrueWithZeroConditionsFails`                     | `PATCH`  | Reactivation without conditions rejected                                                |
| `patchActiveTrueWithConditionSucceeds`                       | `PATCH`  | Reactivation allowed when condition exists                                              |
| `patchClearingAllOutputsFails`                               | `PATCH`  | Final merged state must retain at least one output                                      |
| `patchTransactionRuleWithCategoryObjectMissingIdFails`       | `PATCH`  | Relationship object requires id                                                         |
| `patchTransactionRuleWithTagObjectMissingIdFails`            | `PATCH`  | Tag objects require id                                                                  |
| `deleteTransactionRuleDeletesConditionsAndTagJoinsOnly`      | `DELETE` | Deletes conditions + join rows; preserves output entities                               |

#### CRUD & criteria — 🟡 JHipster generated (58)

Happy-path CRUD, required-field checks, criteria per field.

### Unit — `TransactionRuleServiceTest` (14) — ✅ custom

Ownership matrix + link rejection + `updateShouldResolveCategoryOwnedByRuleOwner` + `updateShouldRejectSubscriptionNotOwnedByRuleOwner` + strict `updatedAt` update guards.

### E2E — `transaction-rule.cy.ts`

#### Navigation & CRUD UI (7) — ✅

Rehabilitated create/delete (sin selector User).

#### Ownership smoke (3) — ✅ custom

| Test                                                             | What it checks                    |
| ---------------------------------------------------------------- | --------------------------------- |
| `should not render user selector on create form`                 | `[data-cy="user"]` absent         |
| `regular user should not see transaction rules created by admin` | API isolation                     |
| `admin should see transaction rules created by another user`     | Admin `GET` includes other's rule |

### Gaps

| Priority | Layer   | Test                                        | Status  |
| -------- | ------- | ------------------------------------------- | ------- |
| Medium   | IT      | Rule engine on FT create                    | ⏳      |
| Low      | E2E     | UI table isolation                          | ⏳      |
| Medium   | Product | Equal-priority tie-break before rule engine | ⏳ open |

---

## TransactionRuleCondition

**Ownership model:** pattern C — `TransactionRuleCondition` → `TransactionRule` → `User`. Normal users operate only on conditions whose parent rule they own. `ROLE_ADMIN` bypasses visibility on conditions and rules.

> **Breaking change:** `transactionRule` is **immutable after create**. Reparent (including same-owner) is **no longer allowed**. Move condition = delete + create.

**Parent rule:** DTO keeps `@NotNull` on `transactionRule`; mapper ignores it; service resolves from DB on create. PATCH uses `JsonNode`: absent field preserves parent; same `{id}` → OK; different `{id}` → `400` (even same owner); `null` → `400`.

**Position:** `position` is server-managed. Create appends (`0`, then max+1) and ignores client-provided position. PUT/PATCH preserve existing position; explicit same position is a no-op; explicit changed or null position returns `400 invalid`. Delete does not reindex remaining positions.

**Domain rules:** field/operator/value matrices; duplicate guard; DELETE last condition → `TransactionRule.active = false` + `updatedAt`; `ACCOUNT` ids validated vs **rule owner** (`transactionRule.user.login`).

### Summary counts

| Type           | File                                              | Tests  | Custom vs generated                                                                                   |
| -------------- | ------------------------------------------------- | ------ | ----------------------------------------------------------------------------------------------------- |
| Integration IT | `TransactionRuleConditionResourceIT`              | **62** | Custom ownership + parent immutable + server-managed position + domain rules + generated CRUD/filters |
| Unit — service | `TransactionRuleConditionServiceTest`             | **17** | Custom ownership + validations + server-managed position + delete side-effect                         |
| Unit — domain  | `TransactionRuleConditionTest`                    | **2**  | Generated                                                                                             |
| Unit — mapper  | `TransactionRuleConditionMapperTest`              | **1**  | Generated                                                                                             |
| Unit — DTO     | `TransactionRuleConditionDTOTest`                 | **1**  | Generated                                                                                             |
| Frontend unit  | `transaction-rule-condition-form-helpers.spec.ts` | **6**  | Operator matrix, input-kind helpers, second value and case sensitivity helpers                        |
| Frontend UX    | `transaction-rule-condition-ux.spec.tsx`          | **18** | Smart condition form behavior + previous parent/title visibility checks                               |
| E2E            | `transaction-rule-condition.cy.ts`                | **8**  | CRUD/navigation + delete dialog copy                                                                  |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TransactionRuleConditionResourceIT,TransactionRuleConditionServiceTest test
```

### Integration tests — `TransactionRuleConditionResourceIT`

**Stack:** `@IntegrationTest`, `@AutoConfigureMockMvc`, `@WithMockUser` (default login `user`), Testcontainers PostgreSQL.

**Test data:** `createEntity()` persiste una `TransactionRule` del mock user `user`.

#### Ownership & authorization (11) — ✅ custom (keep)

| Test                                                                  | HTTP     | What it checks     |
| --------------------------------------------------------------------- | -------- | ------------------ |
| `getTransactionRuleConditionOwnedByAnotherUserIsNotFound`             | `GET`    | Cross-user → `404` |
| `getAllTransactionRuleConditionsDoesNotIncludeAnotherUsersConditions` | `GET`    | List scoped        |
| `adminCanGetTransactionRuleConditionOwnedByAnotherUser`               | `GET`    | Admin bypass       |
| `putTransactionRuleConditionOwnedByAnotherUserIsNotFound`             | `PUT`    | Cross-user → `400` |
| `patchTransactionRuleConditionOwnedByAnotherUserIsNotFound`           | `PATCH`  | Cross-user → `400` |
| `deleteTransactionRuleConditionOwnedByAnotherUserIsNotFound`          | `DELETE` | Cross-user → `404` |
| `adminCanListAllTransactionRuleConditionsIncludingOtherUsers`         | `GET`    | Admin list all     |
| `adminCanUpdateTransactionRuleConditionOwnedByAnotherUser`            | `PUT`    | Admin update       |
| `adminCanDeleteTransactionRuleConditionOwnedByAnotherUser`            | `DELETE` | Admin delete       |

#### Parent rule — immutable — ✅

| Test                                                                      | HTTP    | What it checks                             |
| ------------------------------------------------------------------------- | ------- | ------------------------------------------ |
| `patchTransactionRuleConditionWithoutTransactionRuleFieldPreservesParent` | `PATCH` | Omit `transactionRule` → preserved         |
| `patchTransactionRuleConditionWithNullTransactionRuleFails`               | `PATCH` | `"transactionRule": null` → `400`          |
| `putTransactionRuleConditionWithSameTransactionRuleIdSucceeds`            | `PUT`   | Same parent id → `200`                     |
| `patchTransactionRuleConditionWithSameTransactionRuleIdSucceeds`          | `PATCH` | Same parent id → `200`                     |
| `putTransactionRuleConditionWithDifferentTransactionRuleIdFails`          | `PUT`   | Different parent (same owner) → `400`      |
| `patchTransactionRuleConditionWithDifferentTransactionRuleIdFails`        | `PATCH` | Different parent (same owner) → `400`      |
| `createTransactionRuleConditionWithRuleOwnedByAnotherUserFails`           | `POST`  | Normal user foreign parent → `400` invalid |
| `adminCanCreateTransactionRuleConditionUnderForeignRule`                  | `POST`  | Admin foreign parent → `201`               |

#### Position — server-managed — ✅

| Test                                                                   | HTTP          | What it checks                                     |
| ---------------------------------------------------------------------- | ------------- | -------------------------------------------------- |
| `createWithoutPositionAssignsNextPosition`                             | `POST`        | Omitted position gets `0` for first condition      |
| `createSecondConditionAssignsMaxPlusOneAndIgnoresClientPosition`       | `POST`        | Client position ignored; server appends max+1      |
| `createAfterDeletingMiddleConditionAssignsMaxPlusOneWithoutReindexing` | `POST/DELETE` | Delete gap remains; next create uses current max+1 |
| `putChangedPositionFails`                                              | `PUT`         | Changed position → `400 invalid`                   |
| `putNullPositionFails`                                                 | `PUT`         | Explicit `position: null` → `400 invalid`          |
| `partialUpdateTransactionRuleConditionWithPatch`                       | `PATCH`       | Omitted position preserves current position        |
| `patchSamePositionSucceedsAndPreservesPosition`                        | `PATCH`       | Same position allowed as no-op                     |
| `patchChangedPositionFails`                                            | `PATCH`       | Changed position → `400 invalid`                   |
| `patchNullPositionFails`                                               | `PATCH`       | Explicit `position: null` → `400 invalid`          |

#### DELETE side-effect (4) — ✅

| Test                                             | What it checks                         |
| ------------------------------------------------ | -------------------------------------- |
| `deleteTransactionRuleConditionSucceeds`         | Condition row deleted; parent survives |
| `deleteLastConditionDisablesParentRule`          | `active=false`; `updatedAt` updated    |
| `deleteNonLastConditionKeepsParentActive`        | Parent stays `active=true`             |
| `createConditionOnInactiveRuleDoesNotReactivate` | Parent stays inactive                  |

#### Field / operator validation — ✅

| Area                                       | Tests                                                                                     |
| ------------------------------------------ | ----------------------------------------------------------------------------------------- |
| TEXT (`DESCRIPTION`, `EXTERNAL_REFERENCE`) | valid ops succeed; `GREATER_THAN`/`BEFORE` fail; `REGEX` invalid pattern fails            |
| ENUM (`FLOW`, `ORIGIN`)                    | `EQUALS`/`IN` succeed; `CONTAINS`/`REGEX` fail                                            |
| AMOUNT                                     | numeric parse; reject `$` and thousands sep; `BETWEEN` rules                              |
| DATE                                       | ISO parse; invalid date fails; invalid operators fail                                     |
| ACCOUNT                                    | owner-valid ids succeed; foreign id fails; admin foreign rule + admin-owned account fails |
| `IN`/`NOT_IN`                              | empty tokens fail; one token succeeds                                                     |
| Merged PATCH                               | `BETWEEN` → `EQUALS` with lingering `secondValue` fails                                   |
| Explicit-null PATCH                        | required field `null` fails; `secondValue: null` clears                                   |
| Duplicate guard                            | same logical condition fails; different rule succeeds; update excludes self               |

#### CRUD & criteria — 🟡 JHipster generated (20)

Happy-path CRUD, required-field checks, criteria per field.

### Unit — `TransactionRuleConditionServiceTest` — ✅

Service tests cover parent immutability, merged-state validation, duplicate normalization, server-managed position assignment/immutability, delete deactivation, and ACCOUNT owner-login resolution.

### Frontend smart form tests — ✅

`transaction-rule-condition-form-helpers.spec.ts` covers:

- `getAllowedOperators()` for text, enum, amount, date, and account fields.
- `requiresSecondValue()` only for `BETWEEN`.
- `supportsCaseSensitive()` only for `DESCRIPTION` / `EXTERNAL_REFERENCE`.
- `getValueInputKind()` for amount/date inputs, flow/origin selects, account selector, and `IN`/`NOT_IN` text input.

`transaction-rule-condition-display.spec.ts` covers normalized embedded condition summaries:

- scalar `EQUALS`;
- `BETWEEN` with value + second value;
- text case-sensitive suffix only when true;
- no raw `false` display;
- non-text conditions ignore case sensitivity in display;
- `IN` / `NOT_IN` readable list phrases;
- missing `secondValue` safe fallback;
- account-id display override when account names are available.

`transaction-rule-condition-ux.spec.tsx` covers:

- dynamic create/edit titles;
- parent preselect from `transactionRuleId`;
- parent read-only in edit;
- position hidden from create/edit and omitted from create/edit payloads;
- operator filtering by selected field;
- `secondValue` only for `BETWEEN`;
- `caseSensitive` only for text fields;
- typed value inputs for amount/date/flow/origin/account;
- `IN`/`NOT_IN` as comma-separated text with helper copy;
- field/operator changes clearing incompatible values;
- `ACCOUNT EQUALS` submitting the selected account id as a string and hidden `caseSensitive=false`.

`transaction-rule-ux.spec.tsx` also verifies the embedded TransactionRule detail table uses the normalized `Condition` summary, does not render raw `Value` / `Second Value` / `Case Sensitive` headers, keeps Edit/Delete, and does not render View.

### E2E — `transaction-rule-condition.cy.ts`

Update delete dialog copy (en/es). Create/delete rehabilitated via API rule seed.

### Gaps

| Priority | Layer | Test                                    | Status |
| -------- | ----- | --------------------------------------- | ------ |
| Medium   | IT    | Rule engine on FT create (#9)           | ⏳     |
| Low      | E2E   | UI ownership smoke (user vs admin list) | ⏳     |

---

## CreditAccountDetails

**Ownership model:** pattern B — `CreditAccountDetails` → `FinancialAccount` → `User`. Normal users operate only on details whose parent account they own. `ROLE_ADMIN` bypasses visibility. **`account` is immutable after create.**

**Parent account:** DTO keeps `@NotNull` on `account`; mapper ignores it; service resolves via `FinancialAccountService.findAccessibleAccountEntity()`. Only `CREDIT_CARD` accounts accepted. Duplicate per account rejected in service. PATCH uses `JsonNode`: absent field preserves parent; different `{ id }` or `null` → `400`.

**Timestamps:** `createdAt` / `updatedAt` are server-owned. Create ignores client values and accepts missing timestamps. PUT/PATCH preserve `createdAt`; explicit null or changed `createdAt`/`updatedAt` → `400 invalid`; same timestamps are accepted as no-ops before successful PUT/PATCH sets `updatedAt = now`.

### Summary counts

| Type           | File                              | Tests  | Custom vs generated                                                                     |
| -------------- | --------------------------------- | ------ | --------------------------------------------------------------------------------------- |
| Integration IT | `CreditAccountDetailsResourceIT`  | **53** | Custom ownership/domain/timestamp/composition-helper coverage + generated CRUD baseline |
| Unit — service | `CreditAccountDetailsServiceTest` | **22** | All custom                                                                              |
| Unit — domain  | `CreditAccountDetailsTest`        | **6**  | Generated                                                                               |
| Unit — mapper  | `CreditAccountDetailsMapperTest`  | **1**  | Generated                                                                               |
| Unit — DTO     | `CreditAccountDetailsDTOTest`     | **1**  | Generated                                                                               |
| E2E            | `credit-account-details.cy.ts`    | **8**  | CRUD/navigation (create rehabilitated via API credit card seed)                         |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=CreditAccountDetailsResourceIT,CreditAccountDetailsServiceTest test
```

### Integration tests — `CreditAccountDetailsResourceIT`

**Test data:** `createEntity()` persiste una `FinancialAccount` `CREDIT_CARD` del mock user `user`.

#### Ownership & authorization (11) — ✅ custom

Same matrix as FinancialTransaction (GET/PUT/PATCH → 400 cross-user; GET/DELETE → 404).

Same matrix as FinancialTransaction (GET/PUT/PATCH → 400 cross-user; GET/DELETE → 404). **Exception:** own accessible DELETE → `400` domain invalid (not `204`).

#### Parent account, timestamps & domain guards (16) — ✅ custom

| Test                                                                                                        | What it checks                                                          |
| ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `createCreditAccountDetailsWithAccountOwnedByAnotherUserFails`                                              | Foreign account → `400`                                                 |
| `createCreditAccountDetailsWithNonCreditCardAccountFails`                                                   | `DEBIT` account → `400`                                                 |
| `createCreditAccountDetailsForAccountThatAlreadyHasDetailsFails`                                            | Duplicate → `400`                                                       |
| `updateCreditAccountDetailsWithDifferentAccountFails`                                                       | Immutable account on PUT → `400`                                        |
| `patchCreditAccountDetailsWithDifferentAccountFails`                                                        | Immutable account on PATCH → `400`                                      |
| `createCreditAccountDetails`                                                                                | Missing timestamps accepted; server sets `createdAt`/`updatedAt`        |
| `createCreditAccountDetailsIgnoresClientProvidedTimestamps`                                                 | Client timestamps ignored on create                                     |
| `createCreditAccountDetailsWithoutCreatedAtSucceeds` / `createCreditAccountDetailsWithoutUpdatedAtSucceeds` | DTO timestamps optional on create                                       |
| `putCreditAccountDetailsWithChangedCreatedAtFails` / `putCreditAccountDetailsWithChangedUpdatedAtFails`     | PUT timestamp changes rejected                                          |
| `putCreditAccountDetailsWithNullCreatedAtFails` / `putCreditAccountDetailsWithNullUpdatedAtFails`           | PUT timestamp null rejected                                             |
| `partialUpdateCreditAccountDetailsWithPatch`                                                                | PATCH omitted timestamps preserves `createdAt` and sets `updatedAt=now` |
| `fullUpdateCreditAccountDetailsWithPatch`                                                                   | PATCH same timestamps accepted; `updatedAt=now`                         |
| `patchCreditAccountDetailsWithChangedCreatedAtFails` / `patchCreditAccountDetailsWithChangedUpdatedAtFails` | PATCH timestamp changes rejected                                        |
| `patchCreditAccountDetailsWithNullCreatedAtFails` / `patchCreditAccountDetailsWithNullUpdatedAtFails`       | PATCH timestamp null rejected                                           |

#### Domain rules — DELETE block & mutable fields (7) — ✅ custom

| Test                                                            | What it checks                                                   |
| --------------------------------------------------------------- | ---------------------------------------------------------------- |
| `deleteCreditAccountDetailsIsNotAllowed`                        | Own DELETE → `400` `error.invalid` `params=creditAccountDetails` |
| `adminDeleteCreditAccountDetailsOwnedByAnotherUserIsNotAllowed` | Admin foreign DELETE → `400` invalid                             |
| `patchCreditLimitIsAllowed`                                     | `creditLimit` mutable                                            |
| `patchStatementDayIsAllowed`                                    | `statementDay` mutable                                           |
| `patchPaymentDueDayIsAllowed`                                   | `paymentDueDay` mutable                                          |
| `patchAnnualInterestRateIsAllowed`                              | `annualInterestRate` mutable                                     |
| `loweringCreditLimitBelowOutstandingBalanceIsAllowed`           | No utilization block when lowering limit                         |

#### PATCH parent semantics (2) — ✅ custom

| Test                                                          | What it checks             |
| ------------------------------------------------------------- | -------------------------- |
| `patchCreditAccountDetailsWithoutAccountFieldPreservesParent` | Omit `account` → preserved |
| `patchCreditAccountDetailsWithNullAccountFails`               | `"account": null` → `400`  |

#### Composition helper read endpoint (3) — ✅ custom

| Test                                                             | What it checks                                            |
| ---------------------------------------------------------------- | --------------------------------------------------------- |
| `getCreditAccountDetailsByAccountId`                             | Owned `FinancialAccount` id resolves its details          |
| `getCreditAccountDetailsByAccountIdOwnedByAnotherUserIsNotFound` | Normal user cannot resolve another user's account details |
| `adminCanGetCreditAccountDetailsByAccountIdOwnedByAnotherUser`   | Admin can resolve by foreign account id                   |

### Unit — `CreditAccountDetailsServiceTest` (22) — ✅ custom

CREDIT_CARD validation, duplicate guard, immutable account, server-owned timestamp create/update/patch guards, PATCH preserve/null, scoped `findAll`, scoped find-by-account helper, delete not accessible, direct delete blocked when accessible.

### E2E — `credit-account-details.cy.ts`

Create rehabilitated; direct DELETE expects `400`; UI account picker filtered to `CREDIT_CARD`; create/edit hide timestamp fields and create payload does not inject fake timestamps.

### Gaps

| Priority | Layer | Test                                    | Status |
| -------- | ----- | --------------------------------------- | ------ |
| Low      | E2E   | UI ownership smoke (user vs admin list) | ⏳     |

---

## ApiAccessToken

**Ownership model:** pattern A — direct `user`. Normal users see/edit/delete only their tokens. `ROLE_ADMIN` bypasses filters.

**Security baseline:** `tokenHash` never returned in GET/list/detail (`@JsonInclude(NON_NULL)` + mapper). `tokenHash` / `tokenPrefix` are server-generated on create and immutable after create. Client-provided secret fields are rejected. PATCH uses `JsonNode` for field presence, including explicit null rejection for immutable/server-owned fields.

**HTTP contracts:** POST uses strict `ApiAccessTokenCreateRequestDTO` (name-only). PUT uses strict `ApiAccessTokenUpdateRequestDTO` (editable fields only). Unknown request fields are rejected with `400`; `ApiAccessTokenDTO` is the read/response DTO.

**Decision 11C ✅:** DELETE allowed even when historical `ApiIngestion`s exist; cascade `ApiAccessTokenPermission` children only; ingestion audit via snapshots on `ApiIngestion`, not FK. See [`DOMAIN-RULES.md` §10](DOMAIN-RULES.md#10-apiaccesstoken).

### Summary counts

| Type           | File                        | Tests  | Custom vs generated                                      |
| -------------- | --------------------------- | ------ | -------------------------------------------------------- |
| Integration IT | `ApiAccessTokenResourceIT`  | **41** | 20 custom (17 ownership + 3 security/11C) + 21 JHipster  |
| Unit — service | `ApiAccessTokenServiceTest` | **8**  | All custom                                               |
| Unit — mapper  | `ApiAccessTokenMapperTest`  | **2**  | 1 custom (`shouldNotExposeTokenHashInDto`) + 1 roundtrip |
| Unit — domain  | `ApiAccessTokenTest`        | **6**  | Generated                                                |
| Unit — DTO     | `ApiAccessTokenDTOTest`     | **1**  | Generated                                                |
| E2E            | `api-access-token.cy.ts`    | **9**  | CRUD + no user/hash on edit                              |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=ApiAccessTokenResourceIT,ApiAccessTokenServiceTest test
```

### Integration tests — `ApiAccessTokenResourceIT`

**Test data:** `createEntity()` asigna mock user `user`.

#### Ownership & authorization (11) — ✅ custom

Standard matrix (GET/DELETE → 404 cross-user; PUT/PATCH → 400).

#### Owner assignment (3) — ✅ custom

| Test                                               | What it checks                          |
| -------------------------------------------------- | --------------------------------------- |
| `createApiAccessTokenWithoutUserInPayloadSucceeds` | No `user` in POST → assigns current     |
| `createApiAccessTokenWithForeignUserFails`         | Unknown/explicit `user` in POST → `400` |
| `updateApiAccessTokenWithUserFails`                | Unknown/explicit `user` in PUT → `400`  |

#### Token security (4) — ✅ custom

| Test                                               | What it checks                                                           |
| -------------------------------------------------- | ------------------------------------------------------------------------ |
| `createApiAccessTokenWithNameOnlyGeneratesSecrets` | POST name-only → server `tokenPrefix`, `status`, timestamps + `rawToken` |
| `createApiAccessTokenWithTokenHashFails`           | Client-provided hash → `400`                                             |
| `createApiAccessTokenWithTokenPrefixFails`         | Client-provided prefix → `400`                                           |
| `updateApiAccessTokenWithDifferentTokenHashFails`  | Immutable hash on PUT → `400`                                            |
| `patchApiAccessTokenWithDifferentTokenPrefixFails` | Immutable prefix on PATCH → `400`                                        |
| `patchApiAccessTokenWithNullTokenPrefixFails`      | Explicit null prefix on PATCH → `400`                                    |
| `patchApiAccessTokenWithNullUpdatedAtFails`        | Explicit null server timestamp on PATCH → `400`                          |

#### PATCH user semantics (2) — ✅ custom

| Test                                                | What it checks          |
| --------------------------------------------------- | ----------------------- |
| `patchApiAccessTokenWithoutUserFieldPreservesOwner` | Omit `user` → preserved |
| `patchApiAccessTokenWithNullUserFails`              | `"user": null` → `400`  |

GET/list/detail responses assert `tokenHash` does not exist.

#### Decision 11C delete semantics (3) — ✅ custom

| Test                                               | What it checks                                          |
| -------------------------------------------------- | ------------------------------------------------------- |
| `deleteApiAccessTokenDoesNotDeleteApiIngestions`   | Token delete preserves `ApiIngestion` rows + snapshots  |
| `deleteApiAccessTokenDeletesPermissionChildren`    | Permissions cascade-deleted with token                  |
| `deleteApiAccessTokenOwnedByAnotherUserIsNotFound` | Cross-user delete → `404` (covered in ownership matrix) |

### Unit — `ApiAccessTokenServiceTest` (8) — ✅ custom

Assign owner, **generate secrets when hash omitted**, duplicate hash, immutable secrets, PATCH null user, scoped `findAll`, delete not accessible, **delete cascades permissions**.

### E2E — `api-access-token.cy.ts`

Create: name-only form; `rawToken` modal with copy button after POST (no auto-redirect on create success).

### Gaps

| Priority | Layer | Test                                                                     | Status    |
| -------- | ----- | ------------------------------------------------------------------------ | --------- |
| Low      | E2E   | UI ownership smoke (user vs admin list)                                  | ⏳        |
| Medium   | IT    | **11C:** revoked/deleted token cannot authenticate new runtime ingestion | ⏳ fase 6 |
| Low      | E2E   | Delete dialog copy: history survives                                     | ⏳        |
| Low      | E2E   | Create reveal modal + copy flow                                          | ⏳        |

---

## ApiAccessTokenPermission

**Ownership model:** pattern C — `ApiAccessTokenPermission` → `ApiAccessToken` → `User`. Normal users operate only on permissions whose parent token they own. `ROLE_ADMIN` bypasses visibility. **`apiAccessToken`, `permission` and `createdAt` are immutable after create.**

**Domain rules in service:** DELETE removes only the permission row (token + sibling permissions + ingestions unaffected). No reparenting; change grant = delete + create. CRUD allowed regardless of parent token REVOKED/EXPIRED — no `token.status` guards here. Runtime enforcement deferred (Fase 6). See [`DOMAIN-RULES.md` §2](DOMAIN-RULES.md#2-apiaccesstokenpermission).

**Parent token:** DTO keeps `@NotNull` on `apiAccessToken`; mapper ignores it on write; service resolves via `ApiAccessTokenService.findAccessibleApiAccessTokenEntity()`. Duplicate `(token, permission)` rejected in service. PATCH uses `JsonNode`: absent field preserves parent; different `{ id }` or `null` → `400`. `createdAt` assigned by server on create.

### Summary counts

| Type           | File                                  | Tests  | Custom vs generated                                |
| -------------- | ------------------------------------- | ------ | -------------------------------------------------- |
| Integration IT | `ApiAccessTokenPermissionResourceIT`  | **37** | 21 custom (ownership + domain rules) + 16 JHipster |
| Unit — service | `ApiAccessTokenPermissionServiceTest` | **11** | All custom                                         |
| Unit — mapper  | `ApiAccessTokenPermissionMapperTest`  | **1**  | Generated                                          |
| Unit — domain  | `ApiAccessTokenPermissionTest`        | **6**  | Generated                                          |
| Unit — DTO     | `ApiAccessTokenPermissionDTOTest`     | **1**  | Generated                                          |
| E2E            | `api-access-token-permission.cy.ts`   | **9**  | CRUD rehabilitated via API token seed              |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=ApiAccessTokenPermissionResourceIT,ApiAccessTokenPermissionServiceTest test
```

### Integration tests — `ApiAccessTokenPermissionResourceIT`

**Test data:** `createEntity()` persiste un `ApiAccessToken` del mock user `user` (hash único por llamada).

#### Ownership & authorization (11) — ✅ custom

Standard matrix (GET/DELETE → 404 cross-user; PUT/PATCH → 400).

#### Domain guards (5) — ✅ custom

| Test                                                                  | What it checks                       |
| --------------------------------------------------------------------- | ------------------------------------ |
| `createApiAccessTokenPermissionWithTokenOwnedByAnotherUserFails`      | Foreign token → `400`                |
| `createApiAccessTokenPermissionForTokenThatAlreadyHasPermissionFails` | Duplicate → `400`                    |
| `updateApiAccessTokenPermissionWithDifferentApiAccessTokenFails`      | Immutable parent on PUT → `400`      |
| `patchApiAccessTokenPermissionWithDifferentApiAccessTokenFails`       | Immutable parent on PATCH → `400`    |
| `updateApiAccessTokenPermissionWithDifferentCreatedAtFails`           | Immutable `createdAt` on PUT → `400` |

#### PATCH parent semantics (2) — ✅ custom

| Test                                                                     | What it checks                    |
| ------------------------------------------------------------------------ | --------------------------------- |
| `patchApiAccessTokenPermissionWithoutApiAccessTokenFieldPreservesParent` | Omit `apiAccessToken` → preserved |
| `patchApiAccessTokenPermissionWithNullApiAccessTokenFails`               | `"apiAccessToken": null` → `400`  |

#### Domain rules — DELETE / CREATE confirmatory (5) — ✅ custom

| Test                                                                       | What it checks                                        |
| -------------------------------------------------------------------------- | ----------------------------------------------------- |
| `deleteApiAccessTokenPermissionLeavesParentTokenIntact`                    | DELETE permission only; parent token survives         |
| `deleteApiAccessTokenPermissionLeavesSiblingPermissionOnSameToken`         | DELETE one row; sibling `(token, permission)` remains |
| `createApiAccessTokenPermissionOnRevokedTokenSucceeds`                     | No `token.status` guard on create                     |
| `createApiAccessTokenPermissionOnExpiredTokenSucceeds`                     | No expiration guard on create                         |
| `createApiAccessTokenPermissionOnDifferentTokenWithSamePermissionSucceeds` | Same grant on another token → `201`                   |

### Unit — `ApiAccessTokenPermissionServiceTest` (11) — ✅ custom

Resolve token, duplicate guard, immutable parent/grant/timestamp, PATCH preserve/null, scoped `findAll`, delete not accessible.

### E2E — `api-access-token-permission.cy.ts`

Create rehabilitated; token selector only on create; `permission`/`apiAccessToken` read-only on edit; no `createdAt` form field.

### Gaps

| Priority | Layer | Test                                    | Status      |
| -------- | ----- | --------------------------------------- | ----------- |
| Medium   | IT    | Runtime permission enforcement (Fase 6) | ⏳ deferred |
| Low      | E2E   | UI ownership smoke (user vs admin list) | ⏳          |

---

## UserDashboardPreference

**Ownership model:** pattern A — direct `user`. Normal users see/edit/delete only their own preference row. `ROLE_ADMIN` bypasses filters. **At most one preference per user** enforced in service via `existsByUserId`.

**Owner:** DTO `user` optional; mapper ignores it; service assigns `currentUser` on create and preserves owner on update/patch. PATCH uses `JsonNode`: absent field preserves owner; `"user": null` → `400`.

### Summary counts

| Type           | File                                 | Tests  | Custom vs generated                                |
| -------------- | ------------------------------------ | ------ | -------------------------------------------------- |
| Integration IT | `UserDashboardPreferenceResourceIT`  | **40** | 24 custom (ownership + domain rules) + 16 JHipster |
| Unit — service | `UserDashboardPreferenceServiceTest` | **13** | All custom                                         |
| Unit — mapper  | `UserDashboardPreferenceMapperTest`  | **1**  | Generated                                          |
| Unit — domain  | `UserDashboardPreferenceTest`        | **6**  | Generated                                          |
| Unit — DTO     | `UserDashboardPreferenceDTOTest`     | **1**  | Generated                                          |
| E2E            | `user-dashboard-preference.cy.ts`    | **9**  | CRUD rehabilitated; no user selector               |

**Run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=UserDashboardPreferenceResourceIT,UserDashboardPreferenceServiceTest test
```

### Integration tests — `UserDashboardPreferenceResourceIT`

**Test data:** `createEntity()` asigna mock user `user`.

#### Ownership & authorization (11) — ✅ custom

Standard matrix (GET/DELETE → 404 cross-user; PUT/PATCH → 400).

#### Owner assignment (3) — ✅ custom

| Test                                                             | What it checks                        |
| ---------------------------------------------------------------- | ------------------------------------- |
| `createUserDashboardPreferenceWithoutUserInPayloadSucceeds`      | No `user` in POST → assigns current   |
| `createUserDashboardPreferenceWithForeignUserAssignsCurrentUser` | Foreign `user` ignored                |
| `updateUserDashboardPreferenceCannotChangeOwner`                 | PUT with other user → owner preserved |

#### 1:1 guard (1) — ✅ custom

| Test                                                             | What it checks             |
| ---------------------------------------------------------------- | -------------------------- |
| `createUserDashboardPreferenceWhenUserAlreadyHasPreferenceFails` | Duplicate per user → `400` |

#### PATCH user semantics (3) — ✅ custom

| Test                                                         | What it checks                            |
| ------------------------------------------------------------ | ----------------------------------------- |
| `patchUserDashboardPreferenceWithoutUserFieldPreservesOwner` | Omit `user` → preserved                   |
| `patchUserDashboardPreferenceWithNullUserFails`              | `"user": null` → `400`                    |
| `patchUserDashboardPreferenceCannotChangeOwner`              | PATCH with foreign user → owner preserved |

#### Domain rules — `configuration` JSON (7) — ✅ custom

| Test                                                               | What it checks                              |
| ------------------------------------------------------------------ | ------------------------------------------- |
| `createUserDashboardPreferenceWithNullConfigurationFails`          | POST `configuration: null` → `400` invalid  |
| `createUserDashboardPreferenceWithBlankConfigurationFails`         | POST whitespace-only → `400` invalid        |
| `createUserDashboardPreferenceWithInvalidJsonConfigurationFails`   | POST non-JSON → `400` invalid               |
| `createUserDashboardPreferenceWithJsonPrimitiveConfigurationFails` | POST JSON primitive → `400` invalid         |
| `updateUserDashboardPreferenceWithInvalidConfigurationFails`       | PUT bad JSON → `400` invalid                |
| `patchUserDashboardPreferenceWithNullConfigurationFails`           | PATCH `configuration: null` → `400` invalid |
| `patchUserDashboardPreferenceWithJsonArrayConfigurationIsAllowed`  | PATCH `[]` → `200`                          |

### Unit — `UserDashboardPreferenceServiceTest` (13) — ✅ custom

Assign owner, duplicate guard, preserve owner, PATCH null user, scoped `findAll`, delete not accessible, configuration validation, PATCH omit `configuration` skips validation.

### E2E — `user-dashboard-preference.cy.ts`

Create rehabilitated; no user selector on form.

### Gaps

| Priority | Layer | Test                                    | Status |
| -------- | ----- | --------------------------------------- | ------ |
| Medium   | IT    | Endpoint `/me` for dashboard UX         | ⏳     |
| Low      | E2E   | UI ownership smoke (user vs admin list) | ⏳     |

---

## InternalTransfer

**Ownership model:** pattern D — `InternalTransfer` → `outgoingTransaction` / `incomingTransaction` → `FinancialAccount` → `User`. Normal users operate only on transfers whose **both legs** belong to their accounts. `ROLE_ADMIN` bypasses visibility. **Transaction legs and `createdAt` are immutable after create.**

**Domain rules in service:** link existing accessible txs with unrestricted origin; distinct accounts; same currency/amount; OUT+IN flows; same owner on both legs (even for admin); duplicate participation guard in either role; server-owned `createdAt`; only normalized `notes` mutable.

### Summary counts

| Type           | File                          | Tests  | Custom vs generated                                      |
| -------------- | ----------------------------- | ------ | -------------------------------------------------------- |
| Integration IT | `InternalTransferResourceIT`  | **47** | 32 custom (ownership + domain guards) + 15 JHipster CRUD |
| Unit — service | `InternalTransferServiceTest` | **13** | All custom                                               |
| Unit — mapper  | `InternalTransferMapperTest`  | **1**  | Generated                                                |
| Unit — domain  | `InternalTransferTest`        | **6**  | Generated                                                |
| Unit — DTO     | `InternalTransferDTOTest`     | **1**  | Generated                                                |
| E2E            | `internal-transfer.cy.ts`     | **8**  | Rehabilitated create/edit/delete                         |

**Quick run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=InternalTransferResourceIT,InternalTransferServiceTest test
```

### Integration tests — `InternalTransferResourceIT`

| Test                                                                    | Expectation                                     |
| ----------------------------------------------------------------------- | ----------------------------------------------- |
| `getInternalTransferOwnedByAnotherUserIsNotFound`                       | Cross-user GET → `404`                          |
| `getAllInternalTransfersDoesNotIncludeAnotherUsersTransfers`            | List scoped                                     |
| `putInternalTransferOwnedByAnotherUserIsNotFound`                       | Cross-user PUT → `400`                          |
| `patchInternalTransferOwnedByAnotherUserIsNotFound`                     | Cross-user PATCH → `400`                        |
| `deleteInternalTransferOwnedByAnotherUserIsNotFound`                    | Cross-user DELETE → `404`                       |
| `adminCanGetInternalTransferOwnedByAnotherUser`                         | Admin GET bypass                                |
| `adminCanListAllInternalTransfersIncludingOtherUsers`                   | Admin list bypass                               |
| `adminCanUpdateInternalTransferOwnedByAnotherUser`                      | Admin PUT bypass                                |
| `adminCanDeleteInternalTransferOwnedByAnotherUser`                      | Admin DELETE bypass                             |
| `createInternalTransferWithOutgoingTransactionOwnedByAnotherUserFails`  | Foreign tx → `400`                              |
| `createInternalTransferWithSameTransactionFails`                        | Same tx both sides → `400`                      |
| `createInternalTransferWithDifferentCurrencyFails`                      | Currency mismatch → `400`                       |
| `createInternalTransferWithDifferentAmountFails`                        | Amount mismatch → `400`                         |
| `createInternalTransferWithWrongFlowsFails`                             | Non OUT/IN → `400`                              |
| `createInternalTransferWithSameAccountFails`                            | Same account → `400`                            |
| `createInternalTransferWithNonManualOriginSucceeds`                     | `FILE_IMPORT` / `API` origin allowed            |
| `createInternalTransferWithAlreadyLinkedTransactionFails`               | Duplicate link → `400`                          |
| `createInternalTransferWithTransactionAlreadyLinkedInOppositeRoleFails` | Reusing a linked tx in either role → `400`      |
| `createInternalTransferWithoutCreatedAtSucceeds`                        | Server `createdAt`                              |
| `createInternalTransferIgnoresClientProvidedCreatedAt`                  | Client `createdAt` ignored on create            |
| `createInternalTransferTrimsNotesAndConvertsBlankToNull`                | Notes normalized before persist                 |
| `putInternalTransferWithAbsentLinksPreservesLinks`                      | PUT missing link fields preserves existing legs |
| `putInternalTransferWithNullOutgoingTransactionFails`                   | PUT explicit `null` link → `400`                |
| `updateInternalTransferWithDifferentOutgoingTransactionFails`           | Immutable leg PUT → `400`                       |
| `patchInternalTransferWithNullOutgoingTransactionFails`                 | `"outgoingTransaction": null` → `400`           |
| `patchInternalTransferWithMissingOutgoingTransactionIdFails`            | Link object without id → `400`                  |
| `patchInternalTransferWithNullCreatedAtFails`                           | `createdAt: null` → `400`                       |
| `patchInternalTransferWithChangedCreatedAtFails`                        | Changed `createdAt` → `400`                     |
| `patchInternalTransferWithNullNotesClearsNotes`                         | `notes: null` clears notes                      |
| `createInternalTransferWithIncomingTransactionOwnedByAnotherUserFails`  | Foreign incoming tx → `400`                     |
| `adminCannotCreateInternalTransferWithCrossOwnerLegs`                   | Admin POST cross-owner → `400`                  |
| `deleteInternalTransferLeavesTransactionsIntact`                        | DELETE solo transfer; txs viven                 |

### Candidate endpoints — `FinancialTransactionResourceIT`

| Test                                                                          | Expectation                                          |
| ----------------------------------------------------------------------------- | ---------------------------------------------------- |
| `getOutgoingInternalTransferCandidatesDoesNotIncludeAnotherUsersTransactions` | User normal: scope por owner                         |
| `getOutgoingInternalTransferCandidatesIncludeOnlyOutUnlinked`                 | OUT + sin link en ningún rol; origin no filtra       |
| `getIncomingInternalTransferCandidatesDoesNotIncludeAnotherUsersTransactions` | User normal: scope por owner                         |
| `getIncomingInternalTransferCandidatesIncludeOnlyInUnlinked`                  | IN + sin link en ningún rol; origin no filtra        |
| `adminOutgoingInternalTransferCandidatesCanIncludeAnotherUsersTransactions`   | Admin ve ajenos; create sigue bloqueando cross-owner |

### Unit — `InternalTransferServiceTest` (13) — ✅ custom

Resolve legs, validate pair, reject duplicate link, immutable legs on update/PATCH, delete accessibility.

### E2E — `internal-transfer.cy.ts`

Seeds two accounts + OUT/IN txs via API; create form uses candidate endpoints; legs read-only on edit; delete rehabilitated.

### Gaps / deuda

| Priority | Layer  | Tema                                                            | Status                                                                           |
| -------- | ------ | --------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Low      | IT     | PATCH `incomingTransaction: null` explícito                     | ⏳ service cubierto                                                              |
| Low      | IT     | PATCH otro outgoing `{id}`                                      | ⏳ PUT cubierto                                                                  |
| Low      | IT     | Incoming ya enlazada (simétrico)                                | ⏳                                                                               |
| Low      | IT     | Outgoing wrong flow / non-MANUAL (simétrico)                    | ⏳                                                                               |
| Low      | E2E    | UI ownership smoke (user vs admin list)                         | ⏳                                                                               |
| —        | Domain | Balances no se recalculan en create/delete                      | ⏳ fuera de scope                                                                |
| —        | Domain | Create atómico out+in+transfer                                  | ⏳ fuera de scope                                                                |
| —        | Domain | Validación posting dates entre patas                            | ⏳ fuera de scope                                                                |
| —        | Domain | FinancialTransaction delete cleanup for linked InternalTransfer | ✅ covered by FinancialTransactionResourceIT                                     |
| —        | Domain | Origin unrestricted for existing transaction legs               | ✅ covered; ingestion-created transfer pairing remains a future product decision |

---

## TransactionIngestion

**Ownership model:** pattern B — `TransactionIngestion` → `account` → `User`. Normal users operan solo ingestions cuya cuenta es accesible. `ROLE_ADMIN` bypass global. **`account` e `ingestionType` inmutables** tras create; `createdAt` / `startedAt` y contadores iniciales en server; `status = PENDING` en create.

**Domain rules in service ✅:** lifecycle/status transitions, final statuses terminal, status-based counter consistency, server-owned `completedAt`, source/error normalization, final FILE/API child metadata guards, and explicit revert/delete cleanup order.

### Summary counts

| Type            | File                               | Tests  | Custom vs generated                      |
| --------------- | ---------------------------------- | ------ | ---------------------------------------- |
| Integration IT  | `TransactionIngestionResourceIT`   | **92** | ownership/domain + JHipster CRUD/filters |
| Unit — service  | `TransactionIngestionServiceTest`  | **13** | All custom                               |
| Unit — domain   | `TransactionIngestionTest`         | **6**  | Generated                                |
| Unit — mapper   | `TransactionIngestionMapperTest`   | **1**  | Generated                                |
| Unit — DTO      | `TransactionIngestionDTOTest`      | **1**  | Generated                                |
| Unit — criteria | `TransactionIngestionCriteriaTest` | **1**  | Generated                                |
| E2E             | `transaction-ingestion.cy.ts`      | **~8** | CRUD rehabilitated; ownership smoke ⏳   |

**Quick run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=TransactionIngestionResourceIT,TransactionIngestionServiceTest test
```

### Integration tests — `TransactionIngestionResourceIT` (ownership + domain)

| Test                                                                              | Expectation                                                 |
| --------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| `getTransactionIngestionOwnedByAnotherUserIsNotFound`                             | Cross-user GET → `404`                                      |
| `getAllTransactionIngestionsDoesNotIncludeAnotherUsersIngestions`                 | List scoped                                                 |
| `putTransactionIngestionOwnedByAnotherUserIsNotFound`                             | Cross-user PUT → `400`                                      |
| `patchTransactionIngestionOwnedByAnotherUserIsNotFound`                           | Cross-user PATCH → `400`                                    |
| `deleteTransactionIngestionOwnedByAnotherUserIsNotFound`                          | Cross-user DELETE → `404`                                   |
| `adminCanGetTransactionIngestionOwnedByAnotherUser`                               | Admin GET bypass                                            |
| `adminCanListAllTransactionIngestionsIncludingOtherUsers`                         | Admin list bypass                                           |
| `adminCanUpdateTransactionIngestionOwnedByAnotherUser`                            | Admin PUT bypass                                            |
| `adminCanDeleteTransactionIngestionOwnedByAnotherUser`                            | Admin DELETE bypass                                         |
| `deleteTransactionIngestionWithRecordLinkedToCreatedFinancialTransactionSucceeds` | FK-safe cleanup: records deleted before linked transactions |
| `createTransactionIngestionWithAccountOwnedByAnotherUserFails`                    | Foreign account → `400`                                     |
| `adminCanCreateTransactionIngestionWithForeignAccount`                            | Admin POST foreign account OK                               |
| `updateTransactionIngestionWithDifferentAccountFails`                             | Immutable account PUT → `400`                               |
| `patchTransactionIngestionWithNullAccountFails`                                   | `"account": null` → `400`                                   |
| `patchTransactionIngestionWithDifferentAccountFails`                              | Cambio account PATCH → `400`                                |
| `updateTransactionIngestionWithDifferentIngestionTypeFails`                       | Immutable `ingestionType` PUT → `400`                       |
| `createTransactionIngestionWithoutServerFieldsSucceeds`                           | Server timestamps + counters + PENDING                      |
| `createTransactionIngestionIgnoresClientStatusAndCompletedAt`                     | POST ignora status/completedAt/errorMessage                 |
| `findAllWhereFileIngestionIsNullIsScopedAndFileOnly`                              | Scoped + FILE only                                          |
| `findAllWhereApiIngestionIsNullIsScopedAndApiOnly`                                | Scoped + API only                                           |

### Unit — `TransactionIngestionServiceTest` (13)

- `save` resolve account + server defaults
- foreign account → error
- update rejects account/ingestionType change
- PATCH null account → error
- transition to final sets `completedAt` when child metadata exists
- final FILE without file metadata → error
- terminal status cannot change
- scoped `findAllWhere*` helpers
- delete not accessible → false; accessible delete calls file/api metadata cleanup, internal-transfer cleanup, FT tag cleanup, ingestion-record unlink/delete, FT delete, then parent delete

### Gaps

| Priority | Layer  | Test                                             | Status    |
| -------- | ------ | ------------------------------------------------ | --------- |
| Low      | E2E    | UI ownership smoke (user vs admin list)          | ⏳        |
| —        | Domain | Runtime ingestion pipeline/idempotency execution | ⏳ fase 6 |

---

## FileIngestion

**Ownership model:** pattern C — `FileIngestion` → `transactionIngestion` → `account` → `User`. Normal users operan solo metadata cuya ingestion padre es accesible. `ROLE_ADMIN` bypass. **`transactionIngestion` inmutable** tras create; parent debe ser `ingestionType = FILE`; guard 1:1.

**Domain rules in service:** parent FILE + 1:1; parent and file metadata immutable after create; strings normalized on create; statement dates mutable with final range validation; `createdAt` server-owned; direct delete blocked and parent delete cleans up child.

### Summary counts

| Type           | File                       | Tests  | Custom vs generated                    |
| -------------- | -------------------------- | ------ | -------------------------------------- |
| Integration IT | `FileIngestionResourceIT`  | **45** | 30 ownership/domain + 15 JHipster CRUD |
| Unit — service | `FileIngestionServiceTest` | **9**  | All custom                             |
| Unit — domain  | `FileIngestionTest`        | **4**  | Generated                              |
| E2E            | `file-ingestion.cy.ts`     | **~8** | CRUD rehabilitated; ownership smoke ⏳ |

**Quick run:**

```bash
./mvnw -ntp -Dskip.installnodenpm -Dskip.npm \
  -Dtest=FileIngestionResourceIT,FileIngestionServiceTest test
```

### Integration tests — `FileIngestionResourceIT` (ownership + domain)

| Test                                                                 | Expectation                                                   |
| -------------------------------------------------------------------- | ------------------------------------------------------------- |
| `getFileIngestionOwnedByAnotherUserIsNotFound`                       | Cross-user GET → `404`                                        |
| `getAllFileIngestionsDoesNotIncludeAnotherUsersFileIngestions`       | List scoped                                                   |
| `putFileIngestionOwnedByAnotherUserIsNotFound`                       | Cross-user PUT → `400`                                        |
| `patchFileIngestionOwnedByAnotherUserIsNotFound`                     | Cross-user PATCH → `400`                                      |
| `deleteFileIngestionOwnedByAnotherUserIsNotFound`                    | Cross-user DELETE → `404`                                     |
| `adminCanGet/List/UpdateFileIngestionOwnedByAnotherUser`             | Admin visibility/update bypass for allowed mutable fields     |
| `adminCanDeleteFileIngestionOwnedByAnotherUser`                      | Direct delete still blocked → `400`                           |
| `createFileIngestionWithTransactionIngestionOwnedByAnotherUserFails` | Foreign parent → `400`                                        |
| `adminCanCreateFileIngestionWithForeignTransactionIngestion`         | Admin POST foreign parent OK                                  |
| `createFileIngestionWithApiTransactionIngestionFails`                | Parent API → `400`                                            |
| `createFileIngestionWithParentThatAlreadyHasFileIngestionFails`      | Guard 1:1 → `400`                                             |
| `updateFileIngestionWithDifferentTransactionIngestionFails`          | Immutable parent PUT → `400`                                  |
| `patchFileIngestionWithNullTransactionIngestionFails`                | `"transactionIngestion": null` → `400`                        |
| `patchFileIngestionWithDifferentTransactionIngestionFails`           | Cambio parent PATCH → `400`                                   |
| `patchFileIngestionWithoutTransactionIngestionFieldPreservesParent`  | Omit parent → preserved                                       |
| `createFileIngestionWithoutCreatedAtSucceeds`                        | Server `createdAt`                                            |
| `createFileIngestionIgnoresClientProvidedCreatedAt`                  | Client `createdAt` ignored                                    |
| `createFileIngestionNormalizesStrings`                               | Trim strings; optional blanks → null; hex checksum lowercased |
| `createFileIngestionWithBlankOriginalFilenameFails`                  | Blank filename → `400`                                        |
| `createFileIngestionWithStatementStartAfterEndFails`                 | Invalid statement range → `400`                               |
| `patchFileIngestionWithNullStatementDatesClearsThem`                 | Statement dates nullable                                      |
| `patchFileIngestionWithFinalInvalidStatementRangeFails`              | Final merged date range validated                             |
| `patchFileIngestionWithImmutableOriginalFilenameFails`               | Immutable filename PATCH → `400`                              |
| `patchFileIngestionWithImmutableFileTypeFails`                       | Immutable file type PATCH → `400`                             |
| `patchFileIngestionWithNullCreatedAtFails`                           | `createdAt: null` → `400`                                     |
| `patchFileIngestionWithChangedCreatedAtFails`                        | Changed `createdAt` → `400`                                   |
| `deleteFileIngestion`                                                | Direct delete accessible row → `400`                          |

### Unit — `FileIngestionServiceTest` (9)

- resolve parent scoped + FILE guard + 1:1 guard
- `createdAt` server on save
- reject parent change / null PATCH
- normalization + statement date range
- reject immutable filename change
- direct delete blocked; scoped list

### Unit — `TransactionIngestionServiceTest` (10)

- parent delete calls `FileIngestionRepository.deleteByTransactionIngestionId()` before deleting parent.

### Gaps

| Priority | Layer  | Test                       | Status    |
| -------- | ------ | -------------------------- | --------- |
| Low      | E2E    | UI ownership smoke         | ⏳        |
| —        | Domain | Upload / parser / pipeline | ⏳ fase 6 |

---

## ApiIngestion

**Ownership model:** pattern C — `ApiIngestion` → `transactionIngestion` → `account` → `User`. **Decision 11C:** no FK to `ApiAccessToken`; token metadata stored as immutable snapshots (`apiTokenIdSnapshot`, `apiTokenPrefixSnapshot`, `apiTokenNameSnapshot`). **Same-owner obligatorio** entre ingestion y token **solo en create** (incluso admin). Normal users operan solo metadata cuya ingestion padre es accesible. `ROLE_ADMIN` bypass de scoping, no de same-owner, immutability, or direct-delete guards.

**API shape:** POST uses `ApiIngestionCreateRequestDTO.apiAccessTokenId` as create-only input to copy snapshots. Read/list/update/PATCH use `ApiIngestionDTO` and must not expose or accept an `apiAccessToken` relation.

### Summary counts

| Type           | File                      | Tests  | Custom vs generated                                                             |
| -------------- | ------------------------- | ------ | ------------------------------------------------------------------------------- |
| Integration IT | `ApiIngestionResourceIT`  | **51** | 36 ownership/domain (incl. 11C + normalization/immutability) + 15 JHipster CRUD |
| Unit — service | `ApiIngestionServiceTest` | **10** | All custom                                                                      |

```bash
./mvnw -Dskip.npm -Dskip.installnodenpm \
  -Dtest=ApiIngestionResourceIT,ApiIngestionServiceTest test
```

### Integration tests — `ApiIngestionResourceIT` (ownership + domain)

| Test                                                                               | Rule                                                               |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `getApiIngestionOwnedByAnotherUserIsNotFound`                                      | Cross-user GET → `404`                                             |
| `getAllApiIngestionsDoesNotIncludeAnotherUsersApiIngestions`                       | List scoped                                                        |
| `put/patch/deleteApiIngestionOwnedByAnotherUserIsNotFound`                         | Cross-user mutating → `400`/`404`                                  |
| `adminCanGet/ListApiIngestionOwnedByAnotherUser`                                   | Admin read/list bypass                                             |
| `adminCanUpdate/DeleteApiIngestionOwnedByAnotherUser`                              | Admin does **not** bypass immutability/direct-delete guard → `400` |
| `adminCanCreateApiIngestionWithForeignParentsFromSameOwner`                        | Admin POST foreign parents same owner OK                           |
| `adminCreateApiIngestionWithCrossOwnerParentsFails`                                | Admin POST cross-owner → `400`                                     |
| `createApiIngestionWithTransactionIngestion/ApiAccessTokenOwnedByAnotherUserFails` | User foreign parent/token → `400`                                  |
| `createApiIngestionWithFileTransactionIngestionFails`                              | Parent FILE → `400`                                                |
| `createApiIngestionWithParentThatAlreadyHasApiIngestionFails`                      | Guard 1:1 → `400`                                                  |
| `createApiIngestionWithDuplicateRequestIdFails`                                    | Duplicate `requestId` → `400`                                      |
| `createApiIngestionNormalizesStrings`                                              | Trim strings; optional blanks → `null`                             |
| `createApiIngestionWithBlankRequiredStringsFails`                                  | Blank `requestId` / `apiVersion` / `endpoint` → `400`              |
| `update/patch*DifferentTransactionIngestion/RequestId/EndpointFails`               | Immutable parent/requestId/API metadata → `400`                    |
| `update/patch*DifferentApiToken*SnapshotFails`                                     | Immutable snapshot fields → `400`                                  |
| `createApiIngestionCopiesTokenSnapshots`                                           | **11C:** create copies id/prefix/name into snapshots               |
| `getApiIngestionRetainsSnapshotsAfterTokenDeleted`                                 | **11C:** GET shows snapshots after token delete                    |
| `renameTokenDoesNotMutateOldIngestionSnapshots`                                    | **11C:** token rename does not change existing snapshots           |
| `createApiIngestionWithoutTimestampsPersistsServerTimestamps`                      | Server timestamps                                                  |
| `createApiIngestionIgnoresClientTimestamps`                                        | Ignora timestamps cliente                                          |
| `findAllWhereApiIngestionIsNullIsScopedAndApiOnly` (+ REST)                        | Helper scoped + API only                                           |

### Unit — `ApiIngestionServiceTest` (10)

- resolve parents scoped + API guard + 1:1 + same-owner (admin included)
- `createdAt`/`receivedAt` server on save
- reject duplicate parent / `requestId`
- reject immutable PUT/PATCH fields
- direct delete blocked when accessible; scoped delete/list behavior

### Gaps

| Priority | Layer  | Test                            | Status       |
| -------- | ------ | ------------------------------- | ------------ |
| Low      | E2E    | Cypress parent candidates smoke | ✅ intercept |
| —        | Domain | Pipeline / idempotency real     | ⏳ fase 6    |

---

## IngestionRecord

**Ownership model:** pattern C — `IngestionRecord` → `transactionIngestion` → `account` → `User`. **Same-owner obligatorio** entre ingestion y `financialTransaction` opcional (incluso admin). Normal users operan solo records cuya ingestion padre es accesible. `ROLE_ADMIN` bypass de scoping, no de same-owner.

### Summary counts

| Type           | File                             | Tests  | Custom vs generated                                        |
| -------------- | -------------------------------- | ------ | ---------------------------------------------------------- |
| Integration IT | `IngestionRecordResourceIT`      | **87** | ownership/domain + JHipster CRUD/filters                   |
| Unit — service | `IngestionRecordServiceTest`     | **7**  | All custom                                                 |
| FT helper IT   | `FinancialTransactionResourceIT` | **+1** | `findAllWhereIngestionRecordIsNullIsScopedAndUnlinkedOnly` |
| E2E            | `ingestion-record.cy.ts`         | **~8** | CRUD/navigation; parent + FT intercepts en create          |

```bash
./mvnw -Dskip.npm -Dskip.installnodenpm \
  -Dtest=IngestionRecordResourceIT,IngestionRecordServiceTest test
```

### Integration tests — `IngestionRecordResourceIT` (ownership + domain)

| Test                                                                                                                  | Rule                                                |
| --------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `getAllIngestionRecordsDoesNotIncludeAnotherUsersRecords`                                                             | List scoped                                         |
| `countIngestionRecordsIsScopedForNormalUser`                                                                          | Count scoped                                        |
| `get/put/patch/deleteIngestionRecordOwnedByAnotherUser*`                                                              | Cross-user → `404`/`400`                            |
| `adminCanGet/UpdateIngestionRecordOwnedByAnotherUser`                                                                 | Admin read/update bypass                            |
| `adminCanDeleteIngestionRecordOwnedByAnotherUser`                                                                     | Admin direct DELETE still blocked → `400`           |
| `adminCanCreateIngestionRecordWithForeignTransactionIngestion`                                                        | Admin POST foreign ingestion OK                     |
| `createIngestionRecordWithTransactionIngestion/FinancialTransactionOwnedByAnotherUserFails`                           | User foreign parent → `400`                         |
| `adminCreateIngestionRecordWithCrossOwnerFinancialTransactionFails`                                                   | Admin cross-owner → `400`                           |
| `createCreatedIngestionRecordWithValidFinancialTransactionSucceeds`                                                   | `CREATED` requires linked FT from same ingestion    |
| `createCreatedIngestionRecordWithoutFinancialTransactionFails`                                                        | `CREATED` without FT → `400`                        |
| `createCreatedIngestionRecordWithErrorDetailsFails`                                                                   | `CREATED` cannot have errors                        |
| `createSkippedDuplicateWithFinancialTransactionFails`                                                                 | skipped duplicate forbids FT                        |
| `createRejectedWithErrorMessageSucceeds`                                                                              | `REJECTED` requires normalized errorMessage         |
| `createRejectedWithoutErrorMessageFails`                                                                              | blank errorMessage → `400`                          |
| `createIngestionRecordWithFinancialTransactionAlreadyLinkedFails`                                                     | Guard FT 1:1 → `400`                                |
| `createIngestionRecordWithDuplicateRecordIndexFails`                                                                  | Duplicate `recordIndex` → `400`                     |
| `createIngestionRecordWithDuplicateExternalRecordIdForSameParentFails`                                                | Duplicate normalized external id per parent → `400` |
| `createIngestionRecordAllowsSameRecordIndexAndExternalRecordIdInDifferentParents`                                     | uniqueness is parent-scoped                         |
| `update/patch*DifferentTransactionIngestion/FinancialTransaction/RecordIndex/ExternalRecordId/RawData/CreatedAtFails` | Immutable fields → `400`                            |
| `patch*NullTransactionIngestion/FinancialTransaction/CreatedAtFails`                                                  | PATCH null invalid when final state violates rules  |
| `parentFinalFreezesIngestionRecordChanges`                                                                            | Parent final statuses allow no meaningful changes   |
| `parentPendingAllowsOutcomeUpdates`                                                                                   | Parent pending allows status/error outcome updates  |
| `toStringDoesNotExposeRawDataContents`                                                                                | rawData contents not printed                        |
| `createIngestionRecordWithoutCreatedAtPersistsServerTimestamp`                                                        | Server `createdAt`                                  |
| `createIngestionRecordIgnoresClientCreatedAt`                                                                         | Ignora `createdAt` cliente                          |
| `findAllWhereIngestionRecordIsNullIsScopedAndUnlinkedOnly` (+ REST)                                                   | FT helper scoped + excluye ligadas                  |

### Unit — `IngestionRecordServiceTest` (7)

- resolve parents scoped + same-owner (admin included)
- FT 1:1 + recordIndex unique guards
- `createdAt` server on save
- reject immutable PUT/PATCH fields
- scoped delete

### Gaps

| Priority | Layer  | Test                                     | Status       |
| -------- | ------ | ---------------------------------------- | ------------ |
| Low      | E2E    | Cypress parent + FT candidates intercept | ✅ intercept |
| —        | Domain | Pipeline / status machine real           | ⏳ fase 6    |

---

Copy this block when hardening the next entity:

```markdown
## {EntityName}

**Ownership model:** {direct user | via parent | mixed system+user}

### Summary counts

| Type | File | Tests | Custom vs generated |
| ---- | ---- | ----- | ------------------- |

### Integration — `{Entity}ResourceIT`

#### Ownership (target: ~16 tests)

- create assigns current user
- create without user in payload
- GET/PATCH/PUT/DELETE cross-user denied
- cannot change owner
- admin bypass (GET, list, count, update, delete)

### Unit — `{Entity}ServiceTest`

- save / update / findOne / delete / list scoped rules

### E2E — `{entity}.cy.ts`

- create without user selector
- ownership API smoke (user vs admin)

### Gaps

- ...
```

---

## Changelog

| Date       | Entity                                | Change                                                                                                                                                                                                                                                                                                                                    |
| ---------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-08 | FinancialAccount                      | Initial catalog: 16 IT ownership, 9 service unit, 5 CurrentUser unit, 10 E2E                                                                                                                                                                                                                                                              |
| 2026-07-08 | FinancialTransaction                  | 15 IT custom (12 ownership + 3 domain), 10 service unit, 10 E2E; pattern B via `account`                                                                                                                                                                                                                                                  |
| 2026-07-08 | Tag                                   | 16 IT ownership, 9 service unit, 10 E2E; pattern A clone of FA. DTO `user` sin `@NotNull`. Total ownership suites: ~289 backend tests.                                                                                                                                                                                                    |
| 2026-07-11 | Tag domain rules ✅                   | DELETE with M2M unlink (4 join tables); 9 IT + service cleanup test. 78 IT + 12 service.                                                                                                                                                                                                                                                  |
| 2026-07-11 | Category domain rules ✅              | Block delete with children; leaf cleanup delete; parent immutable; categoryType guards. 103 IT + 16 service.                                                                                                                                                                                                                              |
| 2026-07-11 | Grupo 1 delete dialogs ✅             | Domain-aware confirmation copy (UDP, AATP, Tag, Category); CAD explanation-only. E2E CAD updated.                                                                                                                                                                                                                                         |
| 2026-07-11 | FinancialSubscription domain rules ✅ | DELETE unlink FT + disable rules; owner-scoped links; dates; structural guards; delete dialog UX. 138 IT + 15 service. Grupo 1 complete.                                                                                                                                                                                                  |
| 2026-07-09 | Category                              | 75 IT (16 ownership + 4 hierarchy + 55 generated), 11 service unit, 10 E2E; pattern A + parent validation. Total ownership suites: ~375 backend tests.                                                                                                                                                                                    |
| 2026-07-09 | Budget                                | 99 IT (16 ownership + 4 M2M + 79 generated), 11 service unit, 10 E2E; pattern A + M2M link validation. Total ownership suites: ~485 backend tests.                                                                                                                                                                                        |
| 2026-07-09 | FinancialSubscription                 | 123 IT (16 ownership + 4 links + 103 generated), 12 service unit, 10 E2E; pattern A + link validation. Total ownership suites: ~620 backend tests.                                                                                                                                                                                        |
| 2026-07-09 | FinancialSubscription                 | +5 IT (PATCH preserve/clear links, PUT/PATCH foreign links); 128 IT (16 ownership + 9 links + 103 generated). PATCH uses `JsonNode` for field presence. Total ownership suites: ~625 backend tests.                                                                                                                                       |
| 2026-07-09 | TransactionRule                       | 85 IT (16 ownership + 10 links + 1 owner-scoped admin + 58 generated), 12 service unit, 10 E2E; outputs validated against rule owner. Total ownership suites: ~722 backend tests.                                                                                                                                                         |
| 2026-07-12 | TransactionRule CRUD/domain baseline  | 104 IT, 14 service unit; strict server-owned timestamps, PUT full DTO contract, PATCH JsonNode semantics, output/condition/name rules, delete cleanup. Rule engine and tie-break deferred.                                                                                                                                                |
| 2026-07-11 | TransactionRuleCondition              | Plan: parent immutable (reparent removed); field/operator/value validations; DELETE last condition → deactivate rule. Tests to remove reparent ITs and add ~25+ domain ITs.                                                                                                                                                               |
| 2026-07-09 | TransactionRuleCondition              | 36 IT (16 ownership + ~~reparent~~ + 20 generated), 10 service unit, 8 E2E; pattern C via parent. Total ownership suites: ~768 backend tests.                                                                                                                                                                                             |
| 2026-07-09 | CreditAccountDetails                  | 36 IT (16 ownership/domain + 20 generated), 9 service unit, 8 E2E; pattern B via account, immutable parent, CREDIT_CARD only. Total ownership suites: ~813 backend tests.                                                                                                                                                                 |
| 2026-07-11 | CreditAccountDetails domain rules     | 41 IT (22 custom + 19 generated), 10 service unit; direct DELETE → `400` invalid; mutable credit fields; E2E delete shows explanation (no confirm).                                                                                                                                                                                       |
| 2026-07-09 | ApiAccessToken                        | 38 IT (17 ownership/security + 21 generated), 7 service unit, 9 E2E; pattern A + token security baseline. Total ownership suites: ~858 backend tests.                                                                                                                                                                                     |
| 2026-07-09 | ApiAccessTokenPermission              | 32 IT (16 ownership/domain + 16 generated), 11 service unit, 9 E2E; pattern C via token, immutable parent/grant. Total ownership suites: ~901 backend tests.                                                                                                                                                                              |
| 2026-07-11 | ApiAccessTokenPermission domain rules | 37 IT (21 custom + 16 generated); confirmatory DELETE/CREATE ITs; `READ_TRANSACTIONS` enum for sibling test data.                                                                                                                                                                                                                         |
| 2026-07-09 | UserDashboardPreference               | 33 IT (17 ownership/1:1 + 16 generated), 7 service unit, 9 E2E; pattern A + existsByUserId guard. Total ownership suites: ~941 backend tests.                                                                                                                                                                                             |
| 2026-07-11 | UserDashboardPreference domain rules  | 40 IT (24 custom + 16 generated), 13 service unit; `configuration` JSON validation.                                                                                                                                                                                                                                                       |
| 2026-07-11 | InternalTransfer domain rules         | 47 IT (32 ownership/domain + 15 generated), 13 service unit, 5 candidate IT; origin unrestricted, notes normalization, server-owned `createdAt`, strict link PUT/PATCH, FT delete cleanup.                                                                                                                                                |
| 2026-07-09 | TransactionIngestion                  | Modelo refactor ✅ — 78 IT generated baseline, domain/mapper/criteria updated for `account`; ownership tests ⏳ (pattern B planned ~16 IT + service unit).                                                                                                                                                                                |
| 2026-07-09 | TransactionIngestion                  | 90 IT (20 ownership/domain + 70 generated), 8 service unit; pattern B via `account`, server defaults, scoped helpers. Total ownership suites: ~1090 backend tests.                                                                                                                                                                        |
| 2026-07-11 | FileIngestion domain rules            | 45 IT (30 ownership/domain + 15 generated), 9 service unit, +1 TransactionIngestion service cleanup; immutable file metadata, statement date range, direct delete blocked.                                                                                                                                                                |
| 2026-07-09 | ApiIngestion                          | 44 IT (29 ownership/domain + 15 generated), 9 service unit; pattern C + token + same-owner, parent API + 1:1 + `requestId` unique, server timestamps. Total ownership suites: ~1167 backend tests.                                                                                                                                        |
| 2026-07-09 | IngestionRecord                       | Superseded by 2026-07-12 domain pass; initial ownership baseline was 74 IT + 7 service.                                                                                                                                                                                                                                                   |
| 2026-07-12 | IngestionRecord domain rules          | 87 IT, 7 service unit, +1 FT helper IT; status consistency, parent final freeze, externalRecordId parent-scoped uniqueness, rawData log safety, direct delete blocked.                                                                                                                                                                    |
| 2026-07-12 | FinancialTransaction domain rules     | 101 IT, 10 service unit; JsonNode presence semantics, server timestamps, immutable account/origin/ingestion, owner-scoped links, category/subscription compatibility, internal-transfer guards, delete cleanup.                                                                                                                           |
| 2026-07-12 | FinancialAccount domain rules         | 118 IT, 12 service unit; delete orchestration for ingestion/transaction trees and account-level links, `initialBalanceDate` floor, active no-side-effects.                                                                                                                                                                                |
| 2026-07-13 | FinancialAccount balance read model   | 145 IT, 24 service unit, 8 balance service unit, 18 calculator unit; backend-only `GET /api/financial-accounts/{id}/balance`, strategy calculators by account type, `transactionDate` range, credit-card debt/available credit.                                                                                                           |
| 2026-07-11 | **Decision 11C — snapshot audit**     | Superseded by implementation entry below: removed `ApiIngestion`→`ApiAccessToken` FK; snapshot fields; token delete without ingestion cleanup.                                                                                                                                                                                            |
| 2026-07-11 | **Decision 11C implemented ✅**       | ApiAccessToken: 41 IT (+name-only create, delete preserves ingestions, cascade permissions), 8 service unit. ApiIngestion: 51 IT (+snapshot copy/retain/immutable/rename, normalization, direct delete blocked), 10 service unit. SpaWebFilterIT: forwards `/api-access-token/*` to SPA. Gaps: runtime API auth fase 6, E2E reveal modal. |
