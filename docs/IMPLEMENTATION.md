# FinTrack — Implementation Tracker

Living document for **ownership**, **domain rules**, and **validations** per entity.

Companion docs:

- [`docs/OWNERSHIP-FLOWS.md`](OWNERSHIP-FLOWS.md) — flujos user vs admin por entidad
- [`docs/VALIDATIONS.md`](VALIDATIONS.md) — catálogo de validaciones por entidad y capa
- [`docs/DOMAIN-RULES.md`](DOMAIN-RULES.md) — catálogo de reglas de negocio (delete guards, balances, motor, pipeline)
- [`docs/TESTING.md`](TESTING.md) — what tests exist and how to run them
- [`fintrack.jdl`](../fintrack.jdl) — structural model (baseline only; business logic goes in services)

---

## Macro plan

```
Fase 0  Fundación compartida (CurrentUserService, patrón repository/service/query)
Fase 1  Piloto ownership directo          → FinancialAccount ✅
Fase 2  Ownership indirecto (vía parent)   → FinancialTransaction ✅
Fase 3  Replicar ownership directo        → Tag ✅, Category ✅, Budget ✅, …
Fase 4  Reglas de dominio por entidad     → delete guards, condicionales, motor
Fase 5  Validaciones de negocio (service) → cross-entity, amount > 0, etc.
Fase 6  Ingestion + API + rules engine    → TransactionIngestion, ApiAccessToken, …
```

**Principio:** la capa de verdad es el **service**. REST valida forma (`@Valid`); DB valida estructura; **ownership y negocio viven en service**.

---

## Status legend (per pillar)

| Symbol | Ownership                                                    | Domain rules                             | Validations                                   |
| ------ | ------------------------------------------------------------ | ---------------------------------------- | --------------------------------------------- |
| ✅     | Scoped queries + service + admin bypass + UI sin user picker | Reglas implementadas y testeadas         | Forma (JDL) + negocio en service donde aplica |
| 🟡     | Parcial o solo baseline generado                             | Solo ownership; faltan reglas de negocio | Solo anotaciones JHipster / DTO `@Valid`      |
| ⏳     | No implementado                                              | No implementado                          | Revisión pendiente                            |
| —      | N/A (owned vía otra entidad)                                 | Hereda del parent                        | Hereda del parent                             |

---

## Foundation (compartido entre entidades)

| Componente                | Archivo                                                 | Estado | Notas                                                                                                         |
| ------------------------- | ------------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------- |
| Usuario actual + admin    | `CurrentUserService`                                    | ✅     | `getCurrentUser()`, `getCurrentUserLogin()`, `isAdmin()`                                                      |
| Tests fundación           | `CurrentUserServiceTest`                                | ✅     | 5 unit tests                                                                                                  |
| Patrón repository         | `*Repository` scoped queries                            | 🟡     | FA, FT, Tag, Category, Budget, FinancialSubscription, TransactionRule                                         |
| Patrón service            | assign / filter / preserve owner                        | 🟡     | FA, Tag, Category, Budget, FinancialSubscription, TransactionRule (direct `user`); FT (vía `account`)         |
| Patrón query service      | ownership spec si no admin                              | 🟡     | FA, Tag, Category, Budget, FinancialSubscription, TransactionRule (`user`); FT (`account.user`)               |
| Patrón resource           | delgado; `isAccessible` en PUT/PATCH/DELETE             | 🟡     | FA, FT, Tag, Category, Budget, FinancialSubscription, TransactionRule                                         |
| Patrón DTO                | quitar `@NotNull` en `user` si el cliente no lo manda   | 🟡     | FA, Tag, Category, Budget, FinancialSubscription, TransactionRule — ver nota abajo                            |
| Patrón mapper             | relaciones ignore en `toEntity` / `partialUpdate`       | 🟡     | FA, Tag, Category, Budget, FinancialSubscription, TransactionRule (`user` + links); FT (links)                |
| Patrón UI                 | sin campos que el service asigna / bloquea              | 🟡     | FA, Tag, Category, Budget, FinancialSubscription, TransactionRule sin User; FT sin origin/ingestion en create |
| Resolver links M2M        | accounts/categories/tags owned                          | 🟡     | Budget; FinancialSubscription; TransactionRule (`resulting*`); FT (category/tags/subscription)                |
| PATCH link semantics      | `JsonNode` + `patch.has(field)` en service              | 🟡     | FinancialSubscription, TransactionRule — ver nota abajo                                                       |
| Resolver cuenta accesible | `FinancialAccountService.findAccessibleAccountEntity()` | ✅     | Reutilizado por FT, TransactionIngestion ✅, y futuros hijos de account                                       |

**Nota — `user` en DTO (pattern A):** en `FinancialAccountDTO`, `TagDTO`, `CategoryDTO`, `BudgetDTO`, `FinancialSubscriptionDTO` y `TransactionRuleDTO`, el campo `user` es **opcional en el payload** (sin `@NotNull`). El cliente/UI no envía dueño; el service asigna `currentUser` en create y preserva owner en update. La obligatoriedad sigue en **entity/DB** (`user_id NOT NULL`) y en **service**. En GET la respuesta sí incluye `user.login` (solo lectura).

**Nota — PATCH link semantics:** el resource PATCH recibe `JsonNode`; el service usa `patchNode.has(...)` para distinguir campo **ausente** (preservar link) vs **presente** (`null`/`[]` limpia M2M; `null` limpia ManyToOne). Implementado en Budget, FinancialSubscription, TransactionRule, FinancialAccount (inmutables), etc.

**Nota — TransactionRule outputs:** `resultingCategory` / `resultingTags` se validan contra el **dueño de la rule** (`ownerLogin`), no contra el usuario actual — admin puede editar la rule ajena pero no adjuntar outputs de otro user.

**Convención HTTP cross-user (todas las entidades con ownership):** PUT/PATCH de recurso ajeno → `400` `idnotfound`; GET/DELETE → `404`. No variar por entidad.

### Ownership progress (17 / 17 entidades)

| Entity                   | Pattern                                                | Ownership |
| ------------------------ | ------------------------------------------------------ | --------- |
| FinancialAccount         | A                                                      | ✅        |
| FinancialTransaction     | B                                                      | ✅        |
| Tag                      | A                                                      | ✅        |
| Category                 | A                                                      | ✅        |
| Budget                   | A + M2M                                                | ✅        |
| FinancialSubscription    | A + links                                              | ✅        |
| TransactionRule          | A + links                                              | ✅        |
| TransactionRuleCondition | C — via `transactionRule`                              | ✅        |
| CreditAccountDetails     | B — via `account`                                      | ✅        |
| ApiAccessToken           | A — direct `user`                                      | ✅        |
| ApiAccessTokenPermission | C — via `apiAccessToken`                               | ✅        |
| UserDashboardPreference  | A — direct `user` (1:1)                                | ✅        |
| InternalTransfer         | D — via both tx legs                                   | ✅        |
| TransactionIngestion     | B — via `account`                                      | ✅        |
| FileIngestion            | C — via `transactionIngestion`                         | ✅        |
| ApiIngestion             | C — via `transactionIngestion` + token snapshots (11C) | ✅        |
| IngestionRecord          | C — via `transactionIngestion` + FT                    | ✅        |

---

## Master tracker — 17 entidades

| #   | Entity                       | Pattern                                                | Phase | Ownership | Domain rules | Validations | Tests                                                                                                |
| --- | ---------------------------- | ------------------------------------------------------ | ----- | --------- | ------------ | ----------- | ---------------------------------------------------------------------------------------------------- |
| 1   | **FinancialAccount**         | A — direct `user`                                      | 1     | ✅        | ✅           | ✅          | [TESTING.md § FA](TESTING.md#financialaccount) · [VALIDATIONS §1](VALIDATIONS.md#1-financialaccount) |
| 2   | **FinancialTransaction**     | B — via `account`                                      | 2     | ✅        | ✅           | ✅          | [TESTING.md § FT](TESTING.md#financialtransaction)                                                   |
| 3   | **CreditAccountDetails**     | B — via `account`                                      | 4     | ✅        | ✅           | ✅          | [TESTING.md § CAD](TESTING.md#creditaccountdetails)                                                  |
| 4   | **Category**                 | A — direct `user`                                      | 3     | ✅        | ✅           | ✅          | [TESTING.md § Category](TESTING.md#category)                                                         |
| 5   | **Tag**                      | A — direct `user`                                      | 3     | ✅        | ✅           | ✅          | [TESTING.md § Tag](TESTING.md#tag) · [VALIDATIONS §5](VALIDATIONS.md#5-tag)                          |
| 6   | **TransactionRule**          | A — direct `user`                                      | 3 / 6 | ✅        | ✅           | ✅          | [TESTING.md § TransactionRule](TESTING.md#transactionrule)                                           |
| 7   | **TransactionRuleCondition** | C — via `transactionRule`                              | 6     | ✅        | ✅           | ✅          | [TESTING.md § TRC](TESTING.md#transactionrulecondition)                                              |
| 8   | **FinancialSubscription**    | A + links                                              | 3 / 4 | ✅        | ✅           | ✅          | [TESTING.md § FinancialSubscription](TESTING.md#financialsubscription)                               |
| 9   | **Budget**                   | A + M2M                                                | 3 / 4 | ✅        | ✅           | ✅          | [TESTING.md § Budget](TESTING.md#budget)                                                             |
| 10  | **InternalTransfer**         | D — via 2 transactions                                 | 4     | ✅        | ✅           | ✅          | [TESTING.md § IT](TESTING.md#internaltransfer)                                                       |
| 11  | **TransactionIngestion**     | B — via `account`                                      | 6     | ✅        | ✅           | ✅          | [TESTING.md § TI](TESTING.md#transactioningestion)                                                   |
| 12  | **FileIngestion**            | C — via `transactionIngestion`                         | 6     | ✅        | ✅           | ✅          | [TESTING.md § FI](TESTING.md#fileingestion)                                                          |
| 13  | **ApiIngestion**             | C — via `transactionIngestion` + token snapshots (11C) | 6     | ✅        | ✅ (11C)     | ✅          | [TESTING.md § AI](TESTING.md#apiingestion)                                                           |
| 14  | **IngestionRecord**          | C — via `transactionIngestion`                         | 6     | ✅        | ✅           | ✅          | [TESTING.md § IR](TESTING.md#ingestionrecord) · [VALIDATIONS §14](VALIDATIONS.md#14-ingestionrecord) |
| 15  | **ApiAccessToken**           | A — direct `user`                                      | 3 / 6 | ✅        | ✅ (11C)     | ✅          | [TESTING.md § AAT](TESTING.md#apiaccesstoken)                                                        |
| 16  | **ApiAccessTokenPermission** | C — via `apiAccessToken`                               | 6     | ✅        | ✅           | ✅          | [TESTING.md § AATP](TESTING.md#apiaccesstokenpermission)                                             |
| 17  | **UserDashboardPreference**  | A — direct `user` (1:1 user)                           | 3     | ✅        | ✅           | ✅          | [TESTING.md § UDP](TESTING.md#userdashboardpreference)                                               |

### Delete confirmation dialogs — Grupo 1 UX ✅

**Principle:** Backend service guards remain source of truth. Dialogs are UX-only and explain consequences before delete.

| Entity                       | Dialog behavior                                                                                | i18n keys                                                     |
| ---------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| **UserDashboardPreference**  | Confirm reset; financial data unaffected                                                       | `delete.title`, `delete.message`                              |
| **ApiAccessTokenPermission** | Confirm permission removal; token and history survive                                          | `delete.title`, `delete.message`                              |
| **ApiAccessToken**           | Confirm delete; permissions removed; **ingestion history survives** (snapshot audit 11C)       | `delete.title`, `delete.message`                              |
| **CreditAccountDetails**     | **No delete button** — informational only (direct delete blocked server-side)                  | `delete.title`, `delete.message`                              |
| **Tag**                      | Confirm unlink from FT/rules/budgets/subscriptions; related entities survive                   | `delete.title`, `delete.message`                              |
| **Category**                 | If direct children (`count` API): blocked message, no confirm. Leaf: cleanup message + confirm | `delete.title`, `delete.leafMessage`, `delete.blockedMessage` |
| **FinancialSubscription**    | Confirm unlink from FT; rules disabled; related entities survive                               | `delete.title`, `delete.message`                              |

**Files:** `*-delete-dialog.tsx` per entity; `i18n/en|es/{entity}.json`. Dynamic usage counts (e.g. “Used by 18 transactions”) — nice-to-have, deferred.

### Ownership patterns (referencia)

| Pattern | Descripción                        | Entidades                                                                                                                |
| ------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **A**   | `user` directo required            | FinancialAccount, Category, Tag, TransactionRule, FinancialSubscription, Budget, ApiAccessToken, UserDashboardPreference |
| **B**   | Owned vía relación padre (1 nivel) | FinancialTransaction → account; CreditAccountDetails → account; TransactionIngestion → account                           |
| **C**   | Owned vía cadena (hijo de hijo)    | TransactionRuleCondition, FileIngestion, ApiIngestion, IngestionRecord, ApiAccessTokenPermission                         |
| **D**   | Owned vía reglas compuestas        | InternalTransfer (2 tx, mismo user/moneda)                                                                               |

**Admin:** en todas las entidades user-scoped, `ROLE_ADMIN` debe poder ver y modificar todo (mismo criterio que FinancialAccount).

---

## Per-entity detail

### 1. FinancialAccount ✅ ✅ ✅

**JDL:** `user` required. Balance actual calculado, no persistido.

**Official `initialBalance` semantics:** `FinancialAccount.initialBalance` is the opening position at the beginning of tracking (`posición inicial`). Its meaning depends on `accountType` and sign; it is not always available balance and it is not always debt.

| Account type  | Positive `initialBalance`  | Zero                        | Negative `initialBalance`                              | Future formula                                                 |
| ------------- | -------------------------- | --------------------------- | ------------------------------------------------------ | -------------------------------------------------------------- |
| `DEBIT`       | Starting available balance | No balance                  | Overdraft / negative balance                           | `currentBalance = initialBalance + IN - OUT`                   |
| `CASH`        | Starting cash on hand      | No cash recorded            | Adjustment / negative cash position                    | `currentBalance = initialBalance + IN - OUT`                   |
| `CREDIT_CARD` | Outstanding debt           | No debt / no credit balance | Credit balance / saldo a favor                         | `currentDebt = initialBalance + OUT - IN`                      |
| `INVESTMENT`  | Starting account value     | No value recorded           | Advanced/adjustment case; investment modeling deferred | `currentBalance = initialBalance + IN - OUT`, provisional only |

For `CREDIT_CARD`, `initialBalance` is not `creditLimit` and is not available credit. `CreditAccountDetails.creditLimit` is used later to calculate `availableCredit`. Negative `initialBalance` values are allowed and meaningful; non-negative validation is not a current rule. Service validates monetary scale (`scale <= 2`) and rejects extra decimals instead of rounding.

#### Balance read model ✅

Backend-only calculated snapshot exposed at `GET /api/financial-accounts/{id}/balance?asOfDate=YYYY-MM-DD`.

| Concern           | Decision                                                                                                            |
| ----------------- | ------------------------------------------------------------------------------------------------------------------- |
| Persistence       | No balance/currentDebt/currentValue fields persisted                                                                |
| Access            | Uses `FinancialAccountService.findAccessibleAccountEntity()`; foreign normal-user access is `404`, admin can read   |
| Transaction basis | `transactionDate`, inclusive from `initialBalanceDate` through `asOfDate`; `postingDate` deferred to reconciliation |
| No transactions   | Opening position still produces a snapshot with zero inflow/outflow                                                 |
| Inactive accounts | Balance can still be calculated                                                                                     |
| `CREDIT_CARD`     | Returns `currentDebt`; `availableCredit = creditLimit - currentDebt` only when details/limit exist                  |
| Missing CAD       | Snapshot returns `missingCreditDetails = true`, no error                                                            |
| `INVESTMENT`      | Uses provisional cash-flow formula; investment valuation deferred                                                   |

**Archivos:** `FinancialAccountBalanceService`, `AccountBalanceCalculator`, `DebitBalanceCalculator`, `CashBalanceCalculator`, `CreditCardBalanceCalculator`, `InvestmentBalanceCalculator`, `FinancialAccountBalanceDTO`, `FinancialTransactionRepository`, `FinancialAccountResource`.

#### Ownership ✅

| Regla                                | Implementación                                     |
| ------------------------------------ | -------------------------------------------------- |
| Create asigna `user = currentUser`   | `FinancialAccountService.save()`                   |
| Cliente no elige user                | DTO sin `@NotNull` en `user`; mapper ignora `user` |
| List / criteria filtrados por user   | `FinancialAccountQueryService` + repository        |
| Get / update / patch / delete scoped | `findAccessibleEntity()`                           |
| Admin bypass                         | `CurrentUserService.isAdmin()`                     |
| UI sin User                          | `financial-account-*.tsx`                          |

**Archivos:** `CurrentUserService`, `FinancialAccountRepository`, `FinancialAccountService`, `FinancialAccountQueryService`, `FinancialAccountResource`, `FinancialAccountDTO`, `FinancialAccountMapper`, UI.

#### Domain rules ✅

| Regla                                          | Estado | Notas                                                                              |
| ---------------------------------------------- | ------ | ---------------------------------------------------------------------------------- |
| Usuario no puede ver/editar cuenta ajena       | ✅     |                                                                                    |
| No cambiar dueño en update/patch               | ✅     |                                                                                    |
| Admin accede a todo                            | ✅     |                                                                                    |
| Delete orchestration                           | ✅     | TI tree → remaining FT → budget links → subscriptions null → CAD → account         |
| `initialBalanceDate` floor                     | ✅     | no floor without txs; otherwise `<= earliest transactionDate`                      |
| `initialBalance` mutable                       | ✅     | opening position; positive/zero/negative allowed; no balance recalculation         |
| Monetary scale validation for `initialBalance` | ✅     | `scale <= 2`; reject without rounding; no non-negative rule                        |
| `active` mutable                               | ✅     | no side effects                                                                    |
| Balance actual/current position formulas       | ✅     | backend-only read model; no persisted balance fields; UI/charts/dashboard deferred |

#### Validations ✅

| Capa              | Estado | Detalle                                                                                                                                                                                          |
| ----------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| DTO `@Valid`      | ✅     | name, enums, pattern color/last4, required fields; `user` optional                                                                                                                               |
| Entity JPA        | ✅     | Mismas constraints                                                                                                                                                                               |
| DB Liquibase      | ✅     | `user_id NOT NULL`, FK                                                                                                                                                                           |
| Service — negocio | ✅     | `currency` / `accountType` immutable; server-owned `createdAt` / `updatedAt`; owner preserve; scoped read/write; delete orchestration; initialBalanceDate floor; `initialBalance` monetary scale |
| REST              | ✅     | `@Valid` POST/PUT; **PATCH JsonNode**; `IllegalArgumentException` → `400 invalid`                                                                                                                |
| UI                | ✅     | Timestamp fields hidden; `currency` / `accountType` locked on edit; account-type-specific opening-position labels                                                                                |

**Tests:** 138 IT + 24 service — ver [TESTING.md § FA](TESTING.md#financialaccount).

---

### 2. FinancialTransaction ✅ ✅ ✅

**JDL:** sin `user` directo; `account` required. Amount ≥ 0 en JDL (`@DecimalMin("0")`); dominio exige amount **> 0** y escala monetaria de 2 decimales en service.

#### Ownership ✅

| Regla                                         | Implementación                                                                                              |
| --------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Solo tx de **cuentas accesibles**             | `findAccessibleEntity()` → `account.user.login`; admin usa eager global                                     |
| Resolver `account` desde DB por id            | `FinancialAccountService.findAccessibleAccountEntity()`                                                     |
| List / criteria filtrados por cuenta del user | `FinancialTransactionQueryService` join `account.user`                                                      |
| Get / update / patch / delete scoped          | Mismo patrón que FA                                                                                         |
| Admin bypass                                  | `CurrentUserService.isAdmin()`                                                                              |
| `category`, `tags`, `subscription` opcionales | Validados contra el owner de `transaction.account`; admin editando tx ajena usa el owner de la tx, no admin |

**Archivos:** `FinancialTransactionRepository`, `FinancialTransactionService`, `FinancialTransactionQueryService`, `FinancialTransactionResource`, `FinancialTransactionMapper`, `CategoryRepository`, `TagRepository`, `FinancialSubscriptionRepository`, UI `financial-transaction-update.tsx`.

#### Domain rules ✅

| Regla                                                               | Estado | Implementación                                                                                                                                                       |
| ------------------------------------------------------------------- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Usuario no ve/edita tx de cuenta ajena                              | ✅     | Ownership vía `account`                                                                                                                                              |
| Create server-owned timestamps                                      | ✅     | `createdAt`/`updatedAt` ignorados en POST y seteados a `now`                                                                                                         |
| Create puede setear `transactionIngestion` válida                   | ✅     | Account debe coincidir; origin FILE/API debe corresponder                                                                                                            |
| `account`, `origin`, `transactionIngestion`, `createdAt` inmutables | ✅     | PUT/PATCH presence-aware con `JsonNode`; null/change → `400 invalid`                                                                                                 |
| `updatedAt` server-owned                                            | ✅     | Cliente null/change → `400`; update exitoso setea `now`                                                                                                              |
| Text normalization                                                  | ✅     | Trim; blank optional text → null; description blank invalid                                                                                                          |
| Amount > 0 + scale 2                                                | ✅     | `normalizeAmount()` → `IllegalArgumentException` → REST `400`                                                                                                        |
| InternalTransfer guards                                             | ✅     | Amount/flow inmutables si participa en transfer                                                                                                                      |
| Category compatibility                                              | ✅     | OUT → EXPENSE/BOTH; IN → INCOME/BOTH                                                                                                                                 |
| Subscription compatibility                                          | ✅     | Same owner; currency/account compatible                                                                                                                              |
| Tags/category/subscription PATCH semantics                          | ✅     | Absent preserve; null clear; ids replace; missing ids invalid                                                                                                        |
| Delete cleanup                                                      | ✅     | Linked `IngestionRecord` → `REJECTED` + `FINANCIAL_TRANSACTION_DELETED` + manual-deleted message + unlink; delete transfer link; clear tag join rows; delete only tx |
| Category override vs effective category                             | ⏳     | Futuro motor de reglas                                                                                                                                               |

#### Validations ✅

| Capa              | Estado      | Detalle                                                                                             |
| ----------------- | ----------- | --------------------------------------------------------------------------------------------------- |
| DTO annotations   | ✅ JHipster | transactionDate, description, amount ≥ 0, enums, timestamps remain on DTO                           |
| Entity JPA        | ✅ JHipster | Mismas constraints                                                                                  |
| DB Liquibase      | ✅ JHipster | `account_id NOT NULL`, FK                                                                           |
| Service — negocio | ✅          | Final merged-state validation; owner-scoped links; immutables; cleanup                              |
| REST              | ✅          | POST/PUT/PATCH receive `JsonNode`; `IllegalArgumentException` → `400 invalid`; ownership in service |
| UI                | 🟡          | Existing CRUD UI may still expose fields that backend now treats as server-owned/immutable          |

---

### 3. CreditAccountDetails ✅ ✅ ✅

**JDL:** OneToOne con `FinancialAccount`; solo cuentas `CREDIT_CARD`.

#### Ownership ✅ — Pattern B (vía `FinancialAccount` → `User`)

| Regla                                | Implementación                                                                   |
| ------------------------------------ | -------------------------------------------------------------------------------- |
| Acceso scoped vía padre              | `findAccessibleEntity()` — user normal: `account.user.login`; admin: sin filtro  |
| Create resuelve `account` en service | Mapper ignora `account`; `FinancialAccountService.findAccessibleAccountEntity()` |
| `account` inmutable tras create      | PUT/PATCH con `account` distinto → `400`                                         |
| PATCH `account`                      | Ausente → preservar; `{ id }` distinto → `400`; `null` → `400`                   |
| Solo `CREDIT_CARD`                   | `validateCreditCardAccount()` en create                                          |
| Un details por cuenta                | `existsByAccountId()` antes de save                                              |
| UI mantiene selector                 | Filtrado `CREDIT_CARD` en picker                                                 |

**Archivos:** `CreditAccountDetailsRepository`, `CreditAccountDetailsService`, `CreditAccountDetailsResource` (PATCH `JsonNode`, DELETE domain guard, read-by-account composition helper), `CreditAccountDetailsMapper`, UI.

#### Domain rules ✅

| Regla                                                         | Estado | Notas                                                                                              |
| ------------------------------------------------------------- | ------ | -------------------------------------------------------------------------------------------------- |
| Solo en cuentas `CREDIT_CARD`                                 | ✅     | Validado en service                                                                                |
| Un account solo un details                                    | ✅     | `existsByAccountId` en service                                                                     |
| DELETE directo bloqueado                                      | ✅     | `400` invalid; admin no bypass; `404` si inaccessible                                              |
| Campos mutables (limit, days, rate)                           | ✅     | Sin checks de utilización ni interés                                                               |
| `CREDIT_CARD` expected to have details for full functionality | 📄     | Not enforced by `FinancialAccountService` today; atomic create / required-details guard **futuro** |
| Cascade en FA delete                                          | ✅     | `FinancialAccountService` deletes details during account delete                                    |

**Fuera de scope:** cálculos de interés, statement generation, atomic FA+CAD endpoint, required-details enforcement.

---

### 4. Category ✅ ✅ ✅

**JDL:** `user` required; `parentCategory` opcional (jerarquía self-referential). No `system` flag in v1.

#### Ownership ✅

| Regla                                | Implementación                                                                          |
| ------------------------------------ | --------------------------------------------------------------------------------------- |
| Create asigna `user = currentUser`   | `CategoryService.save()`                                                                |
| Cliente no elige user                | DTO sin `@NotNull` en `user`; mapper ignora `user`                                      |
| List / criteria filtrados por user   | `CategoryQueryService` + repository                                                     |
| Get / update / patch / delete scoped | `findAccessibleEntity()`                                                                |
| Admin bypass                         | `CurrentUserService.isAdmin()`                                                          |
| UI sin User                          | `category-*.tsx`; parent picker en create (scoped por API)                              |
| Link validation (FT)                 | `findOneByIdAndUserLogin` — usado por `FinancialTransactionService` al asignar category |

**Archivos:** `CategoryRepository`, `CategoryService`, `CategoryQueryService`, `CategoryResource`, `CategoryDTO`, `CategoryMapper`, UI.

#### Validations ✅

| Capa              | Estado      | Detalle                                                                                                                                                              |
| ----------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| DTO `@Valid`      | ✅ JHipster | name, categoryType, color pattern, active; timestamps optional/response-owned; `user` **sin** `@NotNull`                                                             |
| Entity JPA        | ✅ JHipster | `user` required                                                                                                                                                      |
| DB Liquibase      | ✅ JHipster | `user_id NOT NULL`, FK                                                                                                                                               |
| Service — negocio | ✅          | Ownership + parent owned on create; server-owned `createdAt` / `updatedAt`; **trim `name`**; **sibling-unique name** (owner + type + parent); inactive in uniqueness |
| REST              | ✅          | `@Valid` en POST/PUT; PATCH `JsonNode` para timestamp presence; `IllegalArgumentException` → `400 invalid`; DELETE domain violations → `400 invalid`                 |
| UI                | ✅          | Sin User picker; sin timestamps; parent picker en create                                                                                                             |

**Timestamp lifecycle:** create accepts missing timestamps and ignores client-provided values; service sets both to `now`. PUT/PATCH preserve `createdAt`; explicit null or changed `createdAt`/`updatedAt` returns `400 invalid`; successful PUT/PATCH sets `updatedAt = now`.

**Tests:** Category ResourceIT + service tests — ver [TESTING.md § Category](TESTING.md#category).

#### Domain rules ✅

| Regla                                  | Estado       | Notas                                                                  |
| -------------------------------------- | ------------ | ---------------------------------------------------------------------- |
| Block delete if direct children        | ✅           | Active + inactive children                                             |
| Leaf delete: cleanup + `deleteById`    | ✅           | FT/FS null; budget M2M; rule `resultingCategory` null + `active=false` |
| `parentCategory` immutable post-create | ✅           | PATCH omit preserves; change → `400`                                   |
| `categoryType` mutable only if unused  | ✅           | In use = children + 4 references                                       |
| Child `categoryType` == parent         | ✅           | Create + type change with parent                                       |
| Default categories on signup           | **Deferred** | Normal user-owned rows; ver § User onboarding / default data           |
| No `Category.system`                   | ✅           | v1 omission                                                            |

**Implementado en:** `CategoryService` + `CategoryRepository` `@Modifying`. Ver [`DOMAIN-RULES.md` §5](DOMAIN-RULES.md#5-category).

#### User onboarding / default data — Deferred

| Item                                                     | Estado       | Notas                                                               |
| -------------------------------------------------------- | ------------ | ------------------------------------------------------------------- |
| Seed default categories per user on signup               | **Deferred** | Separate pass; **not** in `CategoryService.delete()` or CRUD guards |
| Default rows follow normal Category rules after creation | 📄           | Rename / deactivate / delete like any category                      |

---

### 5. Tag ✅ ✅ ✅

#### Ownership ✅

| Regla                                | Implementación                                                                                               |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| Create asigna `user = currentUser`   | `TagService.save()`                                                                                          |
| Cliente no elige user                | DTO sin `@NotNull` en `user`; mapper ignora `user`                                                           |
| Timestamps server-owned              | Create ignora timestamps cliente; PUT/PATCH preservan `createdAt` y setean `updatedAt = now`                 |
| List / criteria filtrados por user   | `TagQueryService` + repository                                                                               |
| Get / update / patch / delete scoped | `findAccessibleEntity()`                                                                                     |
| Admin bypass                         | `CurrentUserService.isAdmin()`                                                                               |
| UI catálogo simple                   | Create/edit sólo `name`, `description`, `color`, `active`; list/detail sin IDs técnicos ni relaciones crudas |
| Link validation (FT)                 | `findOneByIdAndUserLogin` — usado por `FinancialTransactionService` al asignar tags                          |

**Archivos:** `TagRepository`, `TagService`, `TagQueryService`, `TagResource`, `TagDTO`, `TagMapper`, UI.

#### Validations ✅

| Capa              | Estado | Detalle                                                                                                      |
| ----------------- | ------ | ------------------------------------------------------------------------------------------------------------ |
| DTO `@Valid`      | ✅     | name, color pattern, active; timestamps opcionales en DTO porque son server-owned; `user` **sin** `@NotNull` |
| Entity JPA        | ✅     | `user` required                                                                                              |
| DB Liquibase      | ✅     | `user_id NOT NULL`, FK                                                                                       |
| Service — negocio | ✅     | **Trim `name`**; **`name` unique per owner**; timestamps server-owned; uniqueness vs **tag owner** not actor |
| REST              | ✅     | `@Valid` POST/PUT; PATCH `JsonNode` for timestamp presence; `IllegalArgumentException` → `400 invalid`       |

**Tests:** Tag Resource/Service + frontend UX — ver [TESTING.md § Tag](TESTING.md#tag).

#### Domain rules ✅

| Regla                                          | Estado | Notas                                                                                     |
| ---------------------------------------------- | ------ | ----------------------------------------------------------------------------------------- |
| DELETE permitido aunque esté en uso            | ✅     | Unlink M2M primero; no borrar entidades relacionadas                                      |
| Cleanup join tables por `tagId`                | ✅     | 4 tablas: FT, TransactionRule, FinancialSubscription, Budget (`@Modifying` + flush/clear) |
| `active=false` sin borrar links                | ✅     | Alternativa a delete; no requerido antes de delete                                        |
| `name`/`color`/`description`/`active` mutables | ✅     | Uniqueness sin filtrar por `active`                                                       |
| `createdAt`/`updatedAt` server-owned           | ✅     | Cliente no controla timestamps; PUT/PATCH rechazan cambios/null explícitos                |
| Relaciones no editables desde Tag UI           | ✅     | Se preservan; se gestionan desde FT/rules/subscriptions/budgets                           |
| Soft delete                                    | ❌     | Fuera de scope                                                                            |

**Implementado en:** `TagService.delete()` + `TagRepository` `@Modifying` (una transacción). Ver [`DOMAIN-RULES.md` §4](DOMAIN-RULES.md#4-tag).

---

### 6. TransactionRule ✅ ✅ ✅

**JDL:** `user` required; `resultingCategory` opcional (ManyToOne); `resultingTags` opcional (M2M). Evaluada al crear transaction (motor ⏳).

#### Ownership ✅

| Regla                                | Implementación                                                                    |
| ------------------------------------ | --------------------------------------------------------------------------------- |
| Create asigna `user = currentUser`   | `TransactionRuleService.save()`                                                   |
| Cliente no elige user                | DTO sin `@NotNull` en `user`; mapper ignora `user` y links                        |
| List / criteria filtrados por user   | `TransactionRuleQueryService` + repository                                        |
| Get / update / patch / delete scoped | `findAccessibleEntity()` (con bag relationships)                                  |
| Admin bypass                         | `CurrentUserService.isAdmin()`                                                    |
| UI sin User                          | `transaction-rule-*.tsx`                                                          |
| Links owned por **dueño de la rule** | `resolveOptionalCategory/Tags` con `ownerLogin` — **sin bypass admin en outputs** |
| PATCH links                          | `partialUpdate(dto, patchNode)` — `has("resultingCategory")` etc.                 |

**Archivos:** `TransactionRuleRepository`, `TransactionRuleService`, `TransactionRuleQueryService`, `TransactionRuleResource` (PATCH con `JsonNode`), `TransactionRuleDTO`, `TransactionRuleMapper`, UI.

#### Domain rules ✅ (CRUD/domain baseline)

| Regla                                           | Estado | Notas                                                                                                                                                                                                      |
| ----------------------------------------------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Usuario no puede ver/editar rule ajena          | ✅     | Pattern A                                                                                                                                                                                                  |
| No cambiar dueño en update/patch                | ✅     |                                                                                                                                                                                                            |
| Admin accede a todo (CRUD rule)                 | ✅     |                                                                                                                                                                                                            |
| Outputs ⊆ dueño de la rule (aunque admin edite) | ✅     | No mezclar owners rule ↔ category/tag                                                                                                                                                                     |
| PATCH: omitir link preserva; `null`/`[]` limpia | ✅     | `JsonNode` en resource                                                                                                                                                                                     |
| Condiciones hijas scoped al rule                | ✅     | TransactionRuleCondition — pattern C                                                                                                                                                                       |
| Normalización + unicidad de nombre              | ✅     | trim; uniqueness per owner, case-insensitive/trim-insensitive; inactive reserves name                                                                                                                      |
| Description normalization                       | ✅     | trim; blank → `null`                                                                                                                                                                                       |
| Server-owned timestamps                         | ✅     | create sets both; PUT/PATCH reject explicit null/changed `createdAt`/`updatedAt`; successful update sets `updatedAt=now`                                                                                   |
| Server-managed priority/order                   | ✅     | per-user 0-based consecutive ordering; create appends; update/patch preserve; delete reindexes same owner only                                                                                             |
| Active rule requiere conditions                 | ✅     | inactive draft → add conditions → activate                                                                                                                                                                 |
| Rule requiere al menos un output                | ✅     | final merged state                                                                                                                                                                                         |
| Delete cleanup                                  | ✅     | conditions + resultingTags join; no output entities deleted                                                                                                                                                |
| PUT contract                                    | ✅     | Full DTO update; not presence-aware partial semantics. PATCH remains JsonNode                                                                                                                              |
| Parent-centered conditions endpoint             | ✅     | `GET /api/transaction-rules/{id}/conditions`, scoped by parent access, sorted by `position,id`                                                                                                             |
| Rule list ordering                              | ✅     | `TransactionRuleQueryService.findByCriteria` sorts filtered results by `priority ASC, id ASC`                                                                                                              |
| Parent-centered conditions UX                   | ✅     | TransactionRule detail embeds inline add/edit/delete editor; edit remains general rule fields only                                                                                                         |
| Create flow                                     | ✅     | Create hides Active/conditions, submits `active=false`, and redirects to detail for condition management after parent id exists                                                                            |
| Embedded condition form                         | ✅     | Reuses TransactionRuleCondition smart form section/helper; parent hidden/fixed by TransactionRule detail page                                                                                              |
| Embedded condition mutation                     | ✅     | POST includes `transactionRule: { id }`; adding does not auto-activate; PATCH sends editable fields only; DELETE refreshes conditions and parent state                                                     |
| Active toggle UX                                | ✅     | Edit page background-loads condition count and disables Active when empty/unavailable; backend still validates `active=true`                                                                               |
| Product-oriented rule UX                        | ✅     | List/detail use translated status, condition logic, output/result summaries, compact view/edit layout parity, and metadata                                                                                 |
| Priority/order UX                               | ✅     | List/detail show read-only 1-based order; list forces `priority ASC, id ASC`, exposes only possible Move up / Move down controls, and create/edit do not render or submit priority                         |
| Manual reorder endpoint                         | ✅     | `PUT /api/transaction-rules/reorder` accepts full current-user ordered ids, validates exact membership, and normalizes priorities to `0..n`                                                                |
| Edit form hydration                             | ✅     | Edit waits for the requested entity before mounting the JHipster `ValidatedForm`; fields stay direct children for registration/defaults                                                                    |
| Rule Engine Phase 1 evaluator                   | ✅     | Backend-only pure evaluator in `service.rules`; internal `RuleEvaluationResult` supports category/tag suggestions only                                                                                     |
| **Ejecución al crear transaction**              | ✅     | Phase 2 applies rules on FinancialTransaction create with `FILL_EMPTY_ONLY` after resolving a current-user-accessible account; no admin override or cross-user evaluation; not restricted to `MANUAL` only |
| Rule Engine draft preview endpoint              | ✅     | Phase 3A exposes `POST /api/financial-transactions/rule-preview` for unsaved drafts; returns suggestions/conflicts/skips/matches; no save, mutation, UI, or persisted result                               |
| FinancialTransaction manual rule-preview create | ✅     | Phase 3B frontend two-step create: Step 1 details → preview endpoint → Step 2 editable category/tags prefilled from suggestions → normal create save                                                       |
| Drag-and-drop reorder                           | ⏳     | Explicit drag-and-drop UX remains deferred; current implementation is button-based Move up / Move down                                                                                                     |

#### Validations ✅

| Capa                      | Estado      | Detalle                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ------------------------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| DTO `@Valid`              | ✅ JHipster | name, enums, dates; `priority` optional in request because service owns it; `user` opcional en payload                                                                                                                                                                                                                                                                                                                                                                                                           |
| Service — links           | ✅          | Foreign output en create/update/patch → `400`                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| Service — domain baseline | ✅          | Normalization, uniqueness, output requirement, active/conditions, PATCH null semantics, strict timestamp ownership, delete cleanup implemented                                                                                                                                                                                                                                                                                                                                                                   |
| REST                      | ✅          | `@Valid` + `isAccessible` + `IllegalArgumentException` → `400`; cross-user PUT/PATCH → `400`, GET/DELETE → `404`; related conditions endpoint returns `404` when parent inaccessible                                                                                                                                                                                                                                                                                                                             |
| UI                        | ✅          | Create saves inactive parent then redirects to detail; list/detail use compact semantic summaries and read-only order; list Move up / Move down sends full ordered ids to the reorder endpoint; create/edit omit priority; detail/edit share identity/matching/result/status layout; edit preserves JHipster direct-field hydration; detail embeds TransactionRuleCondition editor via existing endpoints; no create-time draft child collection; FinancialTransaction manual create owns Rule Engine preview UI |

#### Rule Engine phases

See [`RULE-ENGINE.md`](RULE-ENGINE.md) for the full design contract.

1. ✅ Pure evaluator service returning `RuleEvaluationResult`; category/tag suggestions only; no mutation, no UI.
2. ✅ Apply on `FinancialTransaction` create using fill-empty-only behavior after resolving an account accessible to the current user and evaluating only that transaction/account owner's rules.
3. ✅ Backend-only draft preview endpoint: `POST /api/financial-transactions/rule-preview`; no save/mutation/application.
4. ✅ Manual create two-step preview UI: Step 1 transaction details → preview endpoint → Step 2 category/tags prepopulated from suggestions and editable before save → normal create save.
5. Reevaluate one transaction.
6. Bulk reevaluation.

**Origin policy note:** no `MANUAL`-only rule-application restriction exists today. Future API/import/ingestion runtime must explicitly decide whether to use central create with rule application, bypass it, make it configurable, preview only, or apply only in specific modes.

---

### 7. TransactionRuleCondition ✅ ✅ ✅

#### Ownership ✅ — Pattern C (vía `TransactionRule` → `User`)

| Regla                                       | Implementación                                                                                                                         |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Acceso scoped vía padre                     | `findAccessibleEntity()` — user normal: `transactionRule.user.login`; admin: sin filtro                                                |
| Create resuelve padre en service            | Mapper ignora `transactionRule`; `resolveTransactionRule()` valida acceso                                                              |
| Normal user create foreign parent           | `400` invalid                                                                                                                          |
| Admin create foreign parent                 | Permitido                                                                                                                              |
| **`transactionRule` inmutable tras create** | PUT/PATCH otro `{id}` → `400` (incluso mismo owner); mismo id → OK; `null` → `400`; PATCH ausente → preservar; mover = delete + create |
| ~~Reparent same-owner~~                     | **Eliminado** — reemplazado por inmutabilidad total                                                                                    |
| PATCH `transactionRule`                     | Ausente → preservar; mismo `{id}` → OK; otro `{id}` → `400`; `null` → `400`                                                            |
| UI mantiene selector                        | Create mantiene selector; edit lo muestra read-only/disabled; query param `transactionRuleId` puede preseleccionar padre               |
| Admin bypass lectura/CRUD                   | Admin opera conditions de rules ajenas                                                                                                 |
| `ACCOUNT` values                            | Validar ids contra **`transactionRule.user.login`**, no admin                                                                          |
| Smart condition form                        | UI filtra operadores por campo, tipa inputs de valor y mantiene parent read-only en edit                                               |
| Embedded collection editor                  | TransactionRule detail manages child conditions inline without exposing parent selector                                                |
| `position` server-managed                   | Create appends using `max(position)+1`; create ignores client `position`; PUT/PATCH preserve and reject changed/null position          |

**Archivos:** `TransactionRuleConditionRepository`, `TransactionRuleConditionService`, `TransactionRuleConditionResource` (PATCH `JsonNode`), `TransactionRuleConditionMapper`, UI, `transaction-rule-condition-form-helpers.ts`, `transaction-rule-condition-form-section.tsx`.

#### Domain rules ✅

| Regla                                           | Estado | Notas                                                                                                                    |
| ----------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------ |
| DELETE solo condition row                       | ✅     | No borrar rule ni FT                                                                                                     |
| DELETE última condition → `rule.active = false` | ✅     | Actualizar `updatedAt`; una transacción                                                                                  |
| Create en rule inactiva no reactiva             | ✅     |                                                                                                                          |
| Field/operator/value compatibility              | ✅     | TEXT / ENUM / AMOUNT / DATE / ACCOUNT matrices                                                                           |
| `IN`/`NOT_IN` token rules                       | ✅     | trim, no empty tokens, canonicalización                                                                                  |
| Duplicate guard (service)                       | ✅     | Normalización + exclude self on update                                                                                   |
| Validar estado final post-merge PATCH           | ✅     | p.ej. `secondValue` huérfano tras cambio de operator                                                                     |
| UI operator filtering                           | ✅     | Mismo matrix que backend: text/enum/amount/date/account                                                                  |
| UI typed value inputs                           | ✅     | amount/date inputs, enum selects, account selector; `IN`/`NOT_IN` remain comma-separated text                            |
| UI `secondValue` / `caseSensitive` visibility   | ✅     | `secondValue` only `BETWEEN`; `caseSensitive` only text fields                                                           |
| Embedded condition summary table                | ✅     | TransactionRule detail table shows a normalized Condition summary instead of raw value/secondValue/caseSensitive columns |
| Embedded parent hidden/fixed                    | ✅     | TransactionRule detail create sends current parent id; edit PATCH omits parent                                           |
| Position hidden from normal UX                  | ✅     | Position is an internal server-managed order; standalone/embedded forms and normal list/detail hide it                   |
| Delete dialog copy                              | ✅     | i18n en/es                                                                                                               |
| ~~Reparent same-owner~~                         | ❌     | Eliminado — parent inmutable                                                                                             |

#### Validations ✅

| Capa               | Estado | Detalle                                                                                                                            |
| ------------------ | ------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| DTO / Entity       | ✅     | DTO accepts omitted client `position`; entity/DB still require server-assigned `position >= 0`                                     |
| Service — parent   | ✅     | Inmutable tras create; PATCH preserve/null/same id                                                                                 |
| Service — position | ✅     | Create appends; PUT/PATCH same value no-op; changed/null position → `400 invalid`; delete does not reindex                         |
| Service — negocio  | ✅     | Matrices field/operator; value parsing; duplicate guard; ACCOUNT vs rule owner                                                     |
| UI helper          | ✅     | Pure helper exposes `getAllowedOperators`, field-kind checks, `requiresSecondValue`, `supportsCaseSensitive`, and value input kind |
| UI form section    | ✅     | Shared by standalone and embedded flows; standalone still shows parent selector; embedded hides it                                 |
| REST               | ✅     | POST/PUT/PATCH use service validation for server-managed position; PATCH JsonNode; `400 invalid`; cross-user PUT/PATCH → `400`     |

**Tests:** 62 IT + 17 service — ver [TESTING.md § TransactionRuleCondition](TESTING.md#transactionrulecondition).

---

### 8. FinancialSubscription ✅ ✅ ✅

**JDL:** `user` required; `account` / `category` opcionales (ManyToOne); `tags` opcional (M2M). No genera transacciones automáticamente.

#### Ownership ✅

| Regla                                | Implementación                                                                                 |
| ------------------------------------ | ---------------------------------------------------------------------------------------------- |
| Create asigna `user = currentUser`   | `FinancialSubscriptionService.save()`                                                          |
| Cliente no elige user                | DTO sin `@NotNull` en `user`; mapper ignora `user` y links                                     |
| List / criteria filtrados por user   | `FinancialSubscriptionQueryService` + repository                                               |
| Get / update / patch / delete scoped | `findAccessibleEntity()` (con bag relationships)                                               |
| Admin bypass                         | `CurrentUserService.isAdmin()`                                                                 |
| UI sin User                          | `financial-subscription-*.tsx`                                                                 |
| Links owned                          | `resolveOptionalAccount` / `resolveOptionalCategory` / `resolveTags` vs **subscription owner** |
| PATCH links                          | `partialUpdate(dto, patchNode)` — `has("account")` / `has("category")` / `has("tags")`         |

**Archivos:** `FinancialSubscriptionRepository`, `FinancialSubscriptionService`, `FinancialSubscriptionQueryService`, `FinancialSubscriptionResource` (PATCH con `JsonNode` + `ObjectMapper`), `FinancialSubscriptionDTO`, `FinancialSubscriptionMapper`, UI.

**Nota:** links `account` / `category` / `tags` se validan contra el **dueño de la subscription** (`ownerLogin`), no contra el usuario actual — admin puede editar la subscription ajena pero no adjuntar links de otro user (mismo patrón que `TransactionRuleService`).

#### Domain rules ✅

| Regla                                                                   | Estado | Notas                                             |
| ----------------------------------------------------------------------- | ------ | ------------------------------------------------- |
| Usuario no puede ver/editar subscription ajena                          | ✅     | Pattern A                                         |
| No cambiar dueño en update/patch                                        | ✅     |                                                   |
| Admin accede a todo                                                     | ✅     |                                                   |
| `account` / `category` / `tags` owned vs **subscription owner**         | ✅     | POST, PUT y PATCH                                 |
| PATCH: omitir link preserva; `null`/`[]` limpia                         | ✅     | `JsonNode` en resource                            |
| `account.currency` == `subscription.currency` cuando `account` presente | ✅     | create/update/patch                               |
| DELETE: unlink FT + disable rules + delete row                          | ✅     | Cleanup explícito                                 |
| `status` PAUSED/CANCELLED preserva links                                | ✅     | Soft-off vs hard delete                           |
| Fechas: `endDate` / `nextExpectedDate` >= `startDate`                   | ✅     | Service validation                                |
| Structural immutability con FT linked                                   | ✅     | `currency`, `recurrenceUnit`, `intervalCount`     |
| Matching en import / tolerancia de monto                                | ⏳     | Fase 6 / motor                                    |
| Delete confirmation UX                                                  | ✅     | `financial-subscription-delete-dialog.tsx` + i18n |

**DELETE cleanup:** `FinancialTransaction.financialSubscription = null`; `rel_financial_subscription__tags` join rows; luego `deleteById`. Account/category/tag entities survive.

#### Validations ✅

| Capa            | Estado      | Detalle                                                                                                          |
| --------------- | ----------- | ---------------------------------------------------------------------------------------------------------------- |
| DTO `@Valid`    | ✅ JHipster | name, enums, dates, required fields; `user` opcional en payload                                                  |
| Service — links | ✅          | Foreign account/category/tag vs subscription owner → `400`                                                       |
| Service — dates | ✅          | `endDate` / `nextExpectedDate` vs `startDate`                                                                    |
| REST            | ✅          | `@Valid` + `isAccessible` + `IllegalArgumentException` → `400`; cross-user PUT/PATCH → `400`, GET/DELETE → `404` |

---

### 9. Budget ✅ ✅ ✅

**JDL:** `user` required; M2M opcional a `accounts`, `categories`, `tags`. Vacío = semántica de reporting (documentada; cálculo fuera de alcance).

#### Ownership ✅

| Regla                                | Implementación                                                           |
| ------------------------------------ | ------------------------------------------------------------------------ |
| Create asigna `user = currentUser`   | `BudgetService.save()`                                                   |
| Cliente no elige user                | DTO sin `@NotNull` en `user`; mapper ignora `user` y M2M                 |
| List / criteria filtrados por user   | `BudgetQueryService` + repository                                        |
| Get / update / patch / delete scoped | `findAccessibleEntity()` (con bag relationships)                         |
| Admin bypass                         | `CurrentUserService.isAdmin()`                                           |
| UI sin User                          | `budget-*.tsx`                                                           |
| M2M links vs **budget owner**        | `resolveAccounts` / `resolveCategories` / `resolveTags` con `ownerLogin` |

**Archivos:** `BudgetRepository`, `BudgetService`, `BudgetQueryService`, `BudgetResource`, `BudgetDTO`, `BudgetMapper`, UI.

#### Domain rules ✅

| Regla                                           | Estado | Notas                                                               |
| ----------------------------------------------- | ------ | ------------------------------------------------------------------- |
| Usuario no puede ver/editar budget ajeno        | ✅     | Pattern A                                                           |
| No cambiar dueño en update/patch                | ✅     |                                                                     |
| Admin accede a todo                             | ✅     | Admin links validados vs **budget owner**, no vs admin              |
| M2M accounts/categories/tags ⊆ owner            | ✅     | `IllegalArgumentException` → REST `400`                             |
| DELETE explícito                                | ✅     | `@Modifying` cleanup 3 join tables → `deleteById` (una transacción) |
| DELETE no toca FA/Category/Tag/FT/Subscription  | ✅     | Solo fila Budget + joins                                            |
| `status` PAUSED/COMPLETED preserva M2M          | ✅     |                                                                     |
| Vacío accounts = todas activas en moneda budget | ✅     | Documentado (reporting)                                             |
| Vacío categories = cualquiera                   | ✅     | Documentado (reporting)                                             |
| Vacío tags = sin filtro por tag                 | ✅     | Documentado (reporting)                                             |

#### Validations ✅

| Capa               | Estado      | Detalle                                                                           |
| ------------------ | ----------- | --------------------------------------------------------------------------------- |
| DTO `@Valid`       | ✅ JHipster | name, amount ≥ 0, enums, dates, timestamps; `user` **sin** `@NotNull`             |
| Service — amount   | ✅          | `amount` > 0 (0 o negativo → `400`)                                               |
| Service — dates    | ✅          | `endDate` ≥ `startDate` cuando set; epoch day 0 = unset                           |
| Service — currency | ✅          | Linked `account.currency` == `budget.currency`; re-valida al cambiar currency     |
| Service — category | ✅          | v1 expense budget: `EXPENSE` o `BOTH` only; `INCOME` → `400`                      |
| Service — links    | ✅          | `ownerLogin`; PATCH `patchNode.has` link semantics                                |
| REST               | ✅          | `@Valid` POST/PUT; **PATCH JsonNode**; `IllegalArgumentException` → `400 invalid` |
| UI                 | ✅          | Sin User picker; delete dialog con copy de dominio; i18n en/es                    |

**Tests:** 125 IT + 17 service — ver [TESTING.md § Budget](TESTING.md#budget).

---

### 10. InternalTransfer ✅ ✅ ✅

#### Ownership ✅ — Pattern D

Vía `outgoingTransaction` / `incomingTransaction` → accounts del mismo user. Scoped queries exigen que **ambas patas** pertenezcan al login actual. Admin bypass en CRUD.

| Regla                                    | Implementación                                                                                                                   |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| List / get scoped por user (ambas patas) | `findAccessibleEntity()` + repository joins                                                                                      |
| Create resuelve txs accesibles           | `FinancialTransactionService.findAccessibleTransactionEntity()`                                                                  |
| Admin bypass                             | `CurrentUserService.isAdmin()`                                                                                                   |
| PUT/PATCH JsonNode doble                 | `outgoingTransaction` / `incomingTransaction`: ausente preserva; `null`/missing id → `400`; mismo `{id}` OK; otro `{id}` → `400` |
| UI selectores filtrados                  | candidatos OUT/IN sin transfer en ningún rol; patas read-only en edit                                                            |

**Archivos:** `InternalTransferRepository`, `InternalTransferService`, `InternalTransferResource`, `InternalTransferMapper`, `InternalTransferDTO`, `FinancialTransactionService` (candidatos + `findAccessibleTransactionEntity`), UI, E2E.

#### Domain rules ✅ (baseline vínculo)

| Regla                        | Implementación                                                                              |
| ---------------------------- | ------------------------------------------------------------------------------------------- |
| Cuentas distintas            | `validateTransferPair()`                                                                    |
| Misma moneda                 | comparación `account.currency`                                                              |
| Mismo monto                  | `amount.compareTo`                                                                          |
| Flows OUT + IN               | `TransactionFlow` guard                                                                     |
| Origen sin restricción       | `MANUAL`, `FILE_IMPORT` y `API` permitidos en cualquier combinación                         |
| Mismo owner en ambas patas   | validación en service (incluso admin)                                                       |
| Tx no participa previamente  | `existsByTransactionIdInEitherRole` (candidatos y create)                                   |
| Patas inmutables tras create | service preserva en PUT/PATCH                                                               |
| `notes` normalizado          | trim; blank/null → `null`; max 500 después de trim                                          |
| Solo `notes` mutable         | service maneja `PATCH notes=null` como clear                                                |
| `createdAt` server-owned     | create ignora cliente y usa `Instant.now()`; PUT/PATCH preservan; cambio/null patch → `400` |
| DELETE permitido             | borra solo `InternalTransfer`; txs quedan                                                   |

**Interacción con FinancialTransaction:** `FinancialTransactionService.delete()` borra primero cualquier vínculo `InternalTransfer` de esa transacción y luego borra sólo esa transacción; no borra la contraparte.

**Fuera de scope:** balances, create atómico out+in+transfer, QueryService.

#### Deuda / riesgos documentados

| Tema                                              | Estado      | Notas                                                                                                              |
| ------------------------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------ |
| Balances                                          | ⏳          | DELETE y create no recalculan saldos                                                                               |
| Create atómico out+in+transfer                    | ⏳          | Solo enlazar txs existentes en este PR                                                                             |
| Posting dates                                     | ⏳          | Sin validación de alineación entre patas                                                                           |
| FT delete cleanup                                 | ✅          | `FinancialTransactionService.delete()` elimina el vínculo InternalTransfer y preserva la contraparte               |
| Origin unrestricted for existing transaction legs | ✅ baseline | `MANUAL` / `FILE_IMPORT` / `API` are allowed; ingestion-created transfer pairing remains a future product decision |
| Admin candidates cross-user                       | ✅ diseño   | Admin ve candidatos ajenos; **create bloquea cross-owner** siempre                                                 |

---

### 11. TransactionIngestion ✅ ✅ ✅

**Modelo (refactor ✅):** `account` ManyToOne **required** (una ingestion = una cuenta; FILE y API). Liquibase: `20260709150500_updated_entity_TransactionIngestion.xml`.

**Ownership ✅ — Pattern B** (como FinancialTransaction): dueño vía `account.user`. `FinancialAccountService.findAccessibleAccountEntity()`.

| Campo / regla                                            | Create                                                                                                                                           | Update / PATCH                                         |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------ |
| `account`                                                | Required; `findAccessibleAccountEntity()`                                                                                                        | **Immutable**                                          |
| `ingestionType`                                          | Required                                                                                                                                         | **Immutable**                                          |
| `createdAt` / `startedAt`                                | Server `now()`                                                                                                                                   | Immutable                                              |
| Contadores (`records*`)                                  | Server `0`                                                                                                                                       | Mutable, required/non-negative; validated by status    |
| `status`                                                 | Server `PENDING`                                                                                                                                 | Lifecycle enforced; final terminal                     |
| `sourceLabel`                                            | Cliente opcional; trim/blank→`null`; max 100 after trim                                                                                          | Mutable                                                |
| `completedAt` / `errorMessage`                           | Ignorados/null                                                                                                                                   | Server-owned completedAt; error allowed only by status |
| Admin                                                    | CRUD ajeno OK; create con cuenta ajena OK                                                                                                        | —                                                      |
| DELETE                                                   | Scoped `404`; explicit revert cleanup: file/api metadata, internal-transfer links, FT tag links, ingestion-record FT links, FTs, records, parent | —                                                      |
| PATCH                                                    | `JsonNode`; `account`/`ingestionType`/server timestamp change → `400`; absent preserves                                                          | —                                                      |
| `findAllWhereFileIngestionIsNull` / `ApiIngestionIsNull` | Scoped + `ingestionType` FILE/API                                                                                                                | —                                                      |

**Fuera de scope:** runtime ingestion pipeline execution and idempotency engine. Standalone `IngestionRecord` domain rules remain separate.

#### Checklist ownership ✅

- [x] Repository: `findAccessibleByAccountUserLogin` + helpers scoped
- [x] Service: resolve account, inmutables, server timestamps/contadores en create
- [x] QueryService: ownership spec si no admin
- [x] Resource: PATCH `JsonNode`, `isAccessible()` en PUT/PATCH/DELETE
- [x] Mapper: ignore server/immutable fields en write
- [x] UI: account/ingestionType read-only en edit; create sin campos server
- [x] IT + ServiceTest ownership

---

### 12. FileIngestion ✅ ✅ ✅

**JDL:** OneToOne con `TransactionIngestion` (required); solo ingestions `ingestionType = FILE`.

**Ownership ✅ — Pattern C** (vía `transactionIngestion.account.user`). Parent resuelto con `TransactionIngestionRepository` scoped — **no** `TransactionIngestionService` custom.

| Campo / regla                                                                                                                              | Create                                                                       | Update / PATCH                                                     |
| ------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `transactionIngestion`                                                                                                                     | Required; repo scoped + `FILE` + sin hijo previo                             | **Immutable**; absent preserves; null/missing/different id → `400` |
| `createdAt`                                                                                                                                | Server `now()`; client value ignored                                         | Server-owned; absent/same preserves; null/changed → `400`          |
| Metadata archivo (`originalFilename`, `fileType`, `contentType`, `fileSizeBytes`, `checksum`, `storageKey`, `parserName`, `parserVersion`) | Normalized then persisted                                                    | **Immutable**                                                      |
| Statement dates                                                                                                                            | Optional; final start <= end                                                 | Mutable; absent preserves; null clears                             |
| Admin                                                                                                                                      | Read/update mutable dates on foreign rows; POST with foreign valid parent OK | Cannot bypass parent/type/immutable/delete guards                  |
| DELETE                                                                                                                                     | Direct delete blocked with `400 invalid`                                     | Parent cleanup via `TransactionIngestionService.delete()`          |
| PATCH / PUT                                                                                                                                | `JsonNode`; presence-aware parent/createdAt semantics                        | —                                                                  |

**Security note:** `storageKey` remains exposed by DTO/UI. It must contain only internal non-secret storage metadata; public URLs/secrets should not be stored there.

**Fuera de scope:** upload/pipeline, status machine del parent.

#### Checklist ownership ✅

- [x] Repository: scoped queries + `existsByTransactionIngestionId` + cleanup delete by parent id
- [x] Service: resolve parent scoped, FILE guard, 1:1 guard, normalization, immutables, `createdAt` server
- [x] Resource: PUT/PATCH `JsonNode`, `isAccessible()`, no `existsById` crudo
- [x] Mapper/DTO: basic Bean Validation shape; service-owned normalization/domain rules; parent/createdAt ignored in write mapper
- [x] UI: picker FILE sin hijo; create sin `createdAt`; parent read-only en edit; delete copy says parent-owned lifecycle
- [x] IT + ServiceTest ownership

---

### 13. ApiIngestion ✅ 🟡 ✅

**JDL:** OneToOne con `TransactionIngestion` (required); parent `ingestionType = API`; **no FK** a `ApiAccessToken`. Snapshot audit: `apiTokenIdSnapshot`, `apiTokenPrefixSnapshot` (max 20), `apiTokenNameSnapshot` (max 100).

**Ownership ✅ — Pattern C** (vía `transactionIngestion.account.user`). Token metadata captured as **immutable snapshots** at create — not a live FK.

| Campo / regla                                                                                | Create                                                                                                                                                      | Update / PATCH   |
| -------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| `transactionIngestion`                                                                       | Required; repo scoped + `API` + sin hijo previo                                                                                                             | **Immutable**    |
| Token snapshots                                                                              | Server copies `id`, `tokenPrefix`, `name` from accessible token at create via `ApiIngestionCreateRequestDTO.apiAccessTokenId` (same-owner guard); **no FK** | **Immutable**    |
| `requestId`                                                                                  | Required; unique global (`existsByRequestId`)                                                                                                               | **Immutable**    |
| `createdAt` / `receivedAt`                                                                   | Server `now()`                                                                                                                                              | Immutable        |
| Metadata API (`idempotencyKey`, `sourceSystem`, `apiVersion`, `endpoint`, `clientReference`) | Cliente; trim, blank optional → `null`                                                                                                                      | **Immutable** v1 |
| Same-owner (create only)                                                                     | `token.user.login == ingestion.account.user.login` — **incluso admin**                                                                                      | —                |
| Admin POST                                                                                   | Padres ajenos OK solo si **mismo owner**                                                                                                                    | —                |
| DELETE                                                                                       | Direct delete bloqueado (`400`); parent cleanup via `TransactionIngestionService`                                                                           | —                |
| PATCH                                                                                        | `JsonNode`; no campos mutables v1; parent / snapshots / `requestId` / metadata / timestamps `null`/change → `400`; absent preserves                         | —                |
| List/read                                                                                    | Muestra snapshots aunque el token fue borrado                                                                                                               | —                |

**Fuera de scope:** pipeline, idempotency real, status machine, delete guards de hijos.

#### Refactor 11C ✅

- [x] JDL + `.jhipster/ApiIngestion.json`: quitar relación `apiAccessToken`; agregar campos snapshot
- [x] Entity: remover `apiAccessToken` FK; agregar snapshots
- [x] Liquibase `20260711160000`: drop `api_access_token_id` FK; add snapshot columns; backfill datos existentes
- [x] DTO/mapper: quitar `apiAccessToken`; exponer snapshots en read; usar `ApiIngestionCreateRequestDTO.apiAccessTokenId` solo en POST
- [x] Service: en create, normalizar strings, resolver token accesible por id → copiar snapshots; quitar persist FK; no mutable fields v1; direct delete blocked
- [x] `ApiAccessToken`: quitar `apiIngestions` one-to-many + `JsonIgnoreProperties`
- [x] UI: create usa token selector solo para captura; edit/list muestra snapshots read-only
- [x] Tests 11C (ver `TESTING.md`)

#### Checklist ownership ✅ (pre-11C baseline)

- [x] Repository: scoped queries + `existsByTransactionIngestionId` + `existsByRequestId`
- [x] Service: resolve parents scoped, same-owner, API + 1:1 guards, server timestamps
- [x] Resource: PATCH `JsonNode`, `isAccessible()`, no `existsById` crudo
- [x] Mapper/DTO: parents / `requestId` / timestamps ignorados en write; timestamps opcionales en POST
- [x] `GET /api/transaction-ingestions/api-ingestion-is-null` (scoped + API)
- [x] UI: parent candidates + tokens scoped en create; parents read-only en edit
- [x] IT + ServiceTest ownership

---

### 14. IngestionRecord ✅ ✅ ✅

**JDL:** `transactionIngestion` required; `financialTransaction` optional 1:1; `recordIndex` required; `createdAt` required en entity/DB.

#### Ownership ✅ — Pattern C + optional FT

| Regla                                          | Implementación                                                                            |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Scoped vía `transactionIngestion.account.user` | `findAccessibleEntity()` + repository eager joins                                         |
| Admin bypass CRUD                              | `CurrentUserService.isAdmin()`                                                            |
| Create resuelve `transactionIngestion` scoped  | admin global / user por login                                                             |
| `financialTransaction` opcional en create      | `FinancialTransactionService.findAccessibleTransactionEntity()`                           |
| **Same-owner** ingestion + FT                  | `validateSameOwner()` — **incluso admin**                                                 |
| FT same-parent guard                           | `financialTransaction.transactionIngestion.id == ingestionRecord.transactionIngestion.id` |
| List / count ownership spec                    | `IngestionRecordQueryService.createSpecification()` — scoped even when criteria is null   |
| PUT/PATCH inmutables                           | JsonNode: ausente preserva; `null`/distinto → `400`                                       |

#### Validaciones baseline ✅

| Regla                                                                                            | Implementación                                                                                                                                                |
| ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `createdAt` server en create                                                                     | `Instant.now()`; DTO sin `@NotNull`                                                                                                                           |
| `transactionIngestion` / `recordIndex` / `externalRecordId` / `rawData` / `createdAt` inmutables | `rejectImmutableFieldChanges()`                                                                                                                               |
| Guard FT 1:1                                                                                     | `existsByFinancialTransactionId()`                                                                                                                            |
| Guard `recordIndex` único por ingestion                                                          | `existsByTransactionIngestionIdAndRecordIndex()`                                                                                                              |
| Guard `externalRecordId` único por ingestion cuando no es null                                   | `existsByTransactionIngestionIdAndExternalRecordId()`                                                                                                         |
| Status consistency                                                                               | `VALID` forbids FT/errors; `IMPORTED` requires FT/no errors; `DISABLED`/`SKIPPED_DUPLICATE` forbid FT; `REJECTED`/`FAILED` forbid FT and require errorMessage |
| Parent final freeze                                                                              | `COMPLETED` / `PARTIALLY_COMPLETED` / `FAILED` allow no-op only                                                                                               |
| Direct delete blocked                                                                            | Records removed by TransactionIngestion cleanup only                                                                                                          |
| rawData logging                                                                                  | entity/DTO `toString()` expose presence/length only                                                                                                           |
| Helper FT sin record                                                                             | `GET /api/financial-transactions/ingestion-record-is-null` (scoped; filtra con `existsByFinancialTransactionId`)                                              |
| UI create                                                                                        | ingestion scoped + FT helper; sin `createdAt`                                                                                                                 |
| UI edit                                                                                          | parents, `recordIndex`, `createdAt` read-only                                                                                                                 |

**Fuera de scope:** pipeline FILE/API, parent count reconciliation, balances/rule-engine execution.

> CSV Ingestion I2A status cleanup: the old `IngestionRecordStatus.CREATED` semantic was removed. CSV preview now uses `VALID` for valid preview rows with `financialTransaction = null`; `IMPORTED` is reserved for later confirm import rows that actually generate a `FinancialTransaction`.

#### Checklist ownership ✅

- [x] Repository: scoped eager joins + `existsByFinancialTransactionId` + `existsByTransactionIngestionIdAndRecordIndex`
- [x] Service: resolve parents scoped, same-owner, guards 1:1 FT + `recordIndex`, server `createdAt`
- [x] QueryService: ownership spec en list/count
- [x] Resource: PATCH `JsonNode`, `isAccessible()`, `delete()` boolean
- [x] Mapper/DTO: parents / `recordIndex` / `createdAt` ignorados en write; `createdAt` opcional en POST
- [x] `GET /api/financial-transactions/ingestion-record-is-null` (scoped; `FinancialTransactionService` vía `existsByFinancialTransactionId`)
- [x] UI: ingestion scoped + FT helper en create; parents/`recordIndex`/`createdAt` read-only en edit
- [x] IT (74) + ServiceTest (7) + FT helper IT (+1)

#### Domain rules ⏳ (fase 6)

Procesamiento FILE/API, status machine, creación de transactions con `origin` FILE/API.

---

### 14b. CSV Ingestion v1 backend I1 ✅

**Design:** [CSV-INGESTION-V1-DESIGN.md](CSV-INGESTION-V1-DESIGN.md)

CSV Ingestion v1 uses the existing ingestion schema. No DB/JDL/Liquibase changes were needed for I1.

#### I1A — canonical CSV parser/validator

| Item        | Plan                                                                                                                          |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Service     | `CanonicalCsvIngestionParser`.                                                                                                |
| Persistence | None.                                                                                                                         |
| Contract    | Exact ordered case-sensitive header: `transactionDate,postingDate,description,signedAmount,currency,externalReference,notes`. |
| Limits      | 2 MB file size; 5,000 data rows.                                                                                              |
| Output      | In-memory parsed rows with raw values, normalized values, errors, and warnings.                                               |
| Tests       | `CanonicalCsvIngestionParserTest`.                                                                                            |

#### I1B — persisted preview endpoint

| Item               | Plan                                                                                                                                                        |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Endpoint           | `POST /api/transaction-ingestions/file-preview` multipart with `accountId` + `file`.                                                                        |
| Ownership          | Resolve account ownership before creating anything. Admin has no special import bypass.                                                                     |
| Parent             | Create `TransactionIngestion` with `ingestionType = FILE`.                                                                                                  |
| File child         | Create `FileIngestion` metadata with `fileType = CSV`, `parserName = fintrack-canonical-csv`, `parserVersion = 1.0`, SHA-256 checksum, `storageKey = null`. |
| Records            | Create one `IngestionRecord` per CSV data row. Store raw/normalized/errors/warnings in `rawData` JSON.                                                      |
| Status             | Valid preview rows use `IngestionRecordStatus.VALID`; `CREATED` is no longer used.                                                                          |
| FT link            | Every I1 `IngestionRecord.financialTransaction` remains `null`.                                                                                             |
| Counters           | `recordsReceived = data rows`; `recordsCreated = 0`; `recordsRejected = invalid rows`; `recordsSkipped = 0`.                                                |
| Dates              | `FileIngestion.statementStartDate/statementEndDate` derive from min/max `transactionDate` among valid rows.                                                 |
| Rejected upload    | Invalid header, missing file, empty file, header-only file, unreadable file, and oversized file create nothing in I1.                                       |
| Duplicate checksum | Same checksum/account is warning-only in I1; it does not block preview.                                                                                     |
| Service            | `CsvIngestionPreviewService`.                                                                                                                               |
| Response           | Return persisted preview DTO with summary counts, global warnings, and rows.                                                                                |
| Not included       | No `FinancialTransaction` creation; no Rule Engine.                                                                                                         |
| Tests              | `CsvIngestionPreviewResourceIT`.                                                                                                                            |

#### I1C — minimal UI

| Item     | Status                                                                                                                                                               |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Entry    | ✅ TransactionIngestion domain workflow at `/transaction-ingestion/file-preview/new`, linked from the TransactionIngestion list; not generated FileIngestion create. |
| Form     | ✅ Select account, upload canonical CSV, submit preview.                                                                                                             |
| Result   | ✅ Show persisted preview summary, non-blocking warnings, preview-only notice, and read-only row table.                                                              |
| Confirm  | ✅ No confirm/import action in I1.                                                                                                                                   |
| Shortcut | Deferred; later FinancialAccount detail shortcut should reuse the same flow with account preselected.                                                                |
| Tests    | ✅ `transaction-ingestion-file-preview.spec.tsx`.                                                                                                                    |

#### I2 — confirm import

| Item        | Plan                                                                                                                                                                           |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Endpoint    | Confirm import endpoint.                                                                                                                                                       |
| Creation    | Create `FinancialTransaction` rows from valid preview records.                                                                                                                 |
| Origin      | Imported transactions use `origin = FILE_IMPORT`.                                                                                                                              |
| Record link | Link each imported `IngestionRecord` to its created `FinancialTransaction`.                                                                                                    |
| Rule Engine | Explicitly apply Rule Engine `FILL_EMPTY_ONLY` during confirm import unless a later design finds a clear issue. This must be tested and must not be an accidental side effect. |
| Counters    | Update import counters according to created/skipped/rejected rows.                                                                                                             |

---

### 15. ApiAccessToken ✅ 🟡 ✅

**JDL:** `user` required; `tokenHash` unique; hashed credential for API ingestion. **No** `apiIngestions` collection (11C).

#### Ownership ✅ — Pattern A

| Regla                              | Implementación                                                                                                                 |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Create asigna `user = currentUser` | `ApiAccessTokenService.save()`                                                                                                 |
| Contratos HTTP específicos         | `ApiAccessTokenCreateRequestDTO` (name-only) + `ApiAccessTokenUpdateRequestDTO` (editable fields only); unknown fields → `400` |
| Cliente no elige user              | POST/PUT con `user` explícito → `400`; service asigna/preserva owner                                                           |
| List / get scoped por user         | `findAccessibleEntity()` + repository                                                                                          |
| Admin bypass                       | `CurrentUserService.isAdmin()`                                                                                                 |
| UI sin User                        | `api-access-token-*.tsx`                                                                                                       |
| PATCH `user`                       | JsonNode: ausente preserva; `null` → `400`; owner inmutable                                                                    |

#### Seguridad baseline ✅

| Regla                                                                       | Implementación                                                        |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `tokenHash` omitido en GET/list/detail                                      | `ApiAccessTokenMapper.toDto` + `@JsonInclude(NON_NULL)`               |
| `tokenHash` / `tokenPrefix` generados por servidor e inmutables tras create | `rejectServerOwnedFieldChanges()` en update/patch                     |
| Duplicado de hash                                                           | DB `UNIQUE`; hash generado con `SecureRandom`                         |
| Create: solo `name` en UI                                                   | Server genera `ftk_…` + SHA-256 hash + prefix + `ACTIVE` + timestamps |
| Reveal-once                                                                 | `rawToken` en respuesta POST; modal con copy en UI                    |

**Fuera de scope (fase 6):** motor API runtime, enforcement `REVOKED`/`expiresAt` en ingestion.

#### Domain rules ✅ (11C baseline)

| Regla                                             | Estado | Notas                                            |
| ------------------------------------------------- | ------ | ------------------------------------------------ |
| DELETE allowed con ingestions históricas          | ✅ 11C | Sin guard por count; cascade permissions primero |
| REVOKE vs DELETE + UI copy                        | ✅ 11C | Historia vía snapshots en `ApiIngestion`         |
| `status` ACTIVE→REVOKED; REVOKED→ACTIVE forbidden | ⏳     | Fase 6                                           |
| `expiresAt` immutable after create                | ⏳     | Fase 6                                           |
| Revocación / expiración en runtime                | ⏳     | Fase 6                                           |
| Permisos explícitos                               | ✅     | `ApiAccessTokenPermission`                       |

#### Refactor 11C ✅

- [x] Remover `Set<ApiIngestion> apiIngestions` y `JsonIgnoreProperties` relacionados
- [x] `delete()`: quitar check `countByApiAccessTokenId`; cascade `ApiAccessTokenPermission` primero
- [x] Delete dialog i18n: historial de ingestion no se borra
- [x] Tests 11C (ver `TESTING.md`)
- [x] `SpaWebFilter`: rutas frontend `/api-access-token` ya no matchean exclusión `/api` (fix 401 en delete)

---

### 16. ApiAccessTokenPermission ✅ ✅ ✅

**JDL:** `permission` enum required; `createdAt` required; ManyToOne `apiAccessToken`.

#### Ownership ✅ — Pattern C

| Regla                                  | Implementación                                                    |
| -------------------------------------- | ----------------------------------------------------------------- |
| Scoped vía `apiAccessToken.user`       | `findAccessibleEntity()` + repository joins                       |
| Create resuelve token accesible        | `ApiAccessTokenService.findAccessibleApiAccessTokenEntity()`      |
| `apiAccessToken` inmutable tras create | `rejectApiAccessTokenChange()`                                    |
| PATCH `apiAccessToken`                 | JsonNode: ausente preserva; `null` → `400`; otro `{ id }` → `400` |
| Admin bypass CRUD                      | `CurrentUserService.isAdmin()`                                    |
| UI: token selector solo en create      | `api-access-token-permission-update.tsx`                          |

#### Validaciones baseline ✅

| Regla                                 | Implementación                                         |
| ------------------------------------- | ------------------------------------------------------ |
| `createdAt` server-assigned en create | `Instant.now()` en `save()`                            |
| `createdAt` / `permission` inmutables | `rejectCreatedAtChange()` / `rejectPermissionChange()` |
| Duplicado `(token, permission)`       | `existsByApiAccessTokenIdAndPermission()`              |

#### Domain rules ✅

| Regla                                     | Estado | Notas                                |
| ----------------------------------------- | ------ | ------------------------------------ |
| DELETE solo la fila permission            | ✅     | Token, siblings, ingestions intactos |
| Sin reparenting / permission mutable      | ✅     | Delete + recreate para cambiar grant |
| CRUD sin guard de `token.status` / expiry | ✅     | Runtime enforcement fuera de scope   |
| Enforcement en ingestion/API              | ⏳     | Fase 6 — deferred                    |

**Fuera de scope:** motor API, runtime enforcement, delete cascade del token padre (#10), QueryService.

---

### 17. UserDashboardPreference ✅ ✅ ✅

**JDL:** `user` required; `configuration` TextBlob (JSON); 1 fila lógica por user.

#### Ownership ✅ — Pattern A + 1:1

| Regla                              | Implementación                                              |
| ---------------------------------- | ----------------------------------------------------------- |
| Create asigna `user = currentUser` | `UserDashboardPreferenceService.save()`                     |
| Cliente no elige user              | DTO `user` opcional; mapper ignora `user`                   |
| List / get scoped por user         | `findAccessibleEntity()` + repository                       |
| Admin bypass                       | `CurrentUserService.isAdmin()`                              |
| UI sin User                        | `user-dashboard-preference-update.tsx`                      |
| PATCH `user`                       | JsonNode: ausente preserva; `null` → `400`; owner inmutable |

#### Validaciones baseline ✅

| Regla                          | Implementación               |
| ------------------------------ | ---------------------------- |
| Máximo una preference por user | `existsByUserId()` en create |

#### Domain rules ✅

| Regla                                        | Estado | Notas                                     |
| -------------------------------------------- | ------ | ----------------------------------------- |
| DELETE simple (solo la fila)                 | ✅     | Sin cascade                               |
| `configuration` required + JSON object/array | ✅     | `validateConfiguration()`; `{}` y `[]` OK |
| PATCH omit `configuration`                   | ✅     | Preserva valor; sin revalidar             |
| Schema widgets / `/me` / upsert              | ⏳     | Deferred                                  |

**Fuera de scope:** endpoint `/me`, dashboard engine, merge JSON profundo, schema validation, QueryService, `UNIQUE(user_id)` Liquibase.

---

## Implementation checklist (copiar por entidad)

Usar al cerrar cada entidad. Marcar en PR / commit.

```markdown
### {EntityName}

**Pattern:** {A|B|C|D}

#### Ownership

- [ ] Repository: `findOneByIdAndUserLogin` / equivalente
- [ ] Service: assign user on create (A) o validate parent (B/C/D)
- [ ] Service: preserve owner on update/patch
- [ ] QueryService: ownership spec si no admin
- [ ] Resource: sin repository directo para exists
- [ ] DTO/mapper: user ignorado si aplica
- [ ] UI: sin picker User (si A)
- [ ] IT: 16 tests ownership (+ admin)
- [ ] Unit: ServiceTest (~9 tests)
- [ ] E2E: create + ownership smoke (si aplica)

#### Domain rules

- [ ] {regla 1}
- [ ] {regla 2}

#### Validations

- [ ] Revisar JDL baseline (DTO/entity/DB)
- [ ] Service: reglas de negocio
- [ ] IT: casos 400 por regla
```

---

## Replication order (recomendado)

| Orden  | Entity                                   | Por qué                                                                                                       |
| ------ | ---------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| 1 ✅   | FinancialAccount                         | Piloto pattern A                                                                                              |
| 2 ✅   | FinancialTransaction                     | Core producto; pattern B                                                                                      |
| 3 ✅   | Tag                                      | Clon rápido pattern A                                                                                         |
| 4 ✅   | Category                                 | Pattern A + parent validation + anti-ciclo                                                                    |
| 5 ✅   | Budget                                   | Pattern A + M2M link validation                                                                               |
| 6 ✅   | FinancialSubscription                    | Pattern A + link validation (ManyToOne + M2M)                                                                 |
| 7 ✅   | TransactionRule                          | Pattern A + links; outputs scoped to rule owner (no admin bypass on links)                                    |
| 8 ✅   | TransactionRuleCondition                 | Pattern C; parent immutable; field/operator validations                                                       |
| 9 ✅   | CreditAccountDetails                     | Pattern B via account; immutable parent; CREDIT_CARD only                                                     |
| 10 ✅  | ApiAccessToken                           | Pattern A + token security baseline                                                                           |
| 11 ✅  | ApiAccessTokenPermission                 | Pattern C via token; immutable parent/grant fields                                                            |
| 12 ✅  | UserDashboardPreference                  | Pattern A + 1:1 per user guard                                                                                |
| 13 ✅  | InternalTransfer                         | Pattern D; baseline vínculo transfer↔tx                                                                      |
| 14a ✅ | TransactionIngestion — modelo            | M2M `accounts` → ManyToOne `account` required; migración Liquibase                                            |
| 14b ✅ | TransactionIngestion — ownership         | Pattern B via `account`; inmutables + PATCH `JsonNode`                                                        |
| 15a ✅ | FileIngestion — ownership + domain rules | Pattern C via `transactionIngestion`; parent scoped repo; FILE + 1:1; immutable metadata; parent-owned delete |
| 15b ✅ | ApiIngestion — ownership + 11C snapshots | Pattern C; immutable snapshots; same-owner at create; Liquibase migration                                     |
| 15c ✅ | IngestionRecord — ownership              | Pattern C + optional FT; same-owner guard; recordIndex unique                                                 |

---

## Changelog

| Date       | Change                                                                                                                                                                                                                                                                                                                                                                       |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2026-07-08 | Initial tracker. FinancialAccount: ownership ✅, domain/validations 🟡. 16 entities ⏳.                                                                                                                                                                                                                                                                                      |
| 2026-07-08 | FinancialTransaction: initial ownership ✅ (pattern B), initial domain baseline ✅ (manual create + amount > 0), validations ✅. Superseded by 2026-07-12 full backend domain pass.                                                                                                                                                                                          |
| 2026-07-08 | Tag: ownership ✅ (pattern A, clon FA). Domain/validations 🟡. DTO `user` opcional en payload. Siguiente: Category.                                                                                                                                                                                                                                                          |
| 2026-07-09 | Category: ownership ✅ (pattern A), domain rules 🟡 (parent owned, no self-parent, anti-ciclo ✅; delete guards ⏳), validations ✅. Siguiente: Budget.                                                                                                                                                                                                                      |
| 2026-07-09 | Budget: ownership ✅ (pattern A + M2M), domain rules 🟡 (links owned ✅; empty-set semantics ⏳), validations ✅. Siguiente: FinancialSubscription.                                                                                                                                                                                                                          |
| 2026-07-09 | FinancialSubscription: ownership ✅ (pattern A + links), domain rules 🟡 (links owned ✅; delete guards / matching ⏳), validations ✅. Siguiente: TransactionRule.                                                                                                                                                                                                          |
| 2026-07-09 | FinancialSubscription: PATCH link semantics ✅ (`JsonNode` + `has(field)`); links owned en PUT/PATCH ✅. Convención HTTP cross-user documentada en Foundation.                                                                                                                                                                                                               |
| 2026-07-09 | TransactionRule: ownership ✅ (pattern A + links), domain rules 🟡 (outputs ⊆ rule owner, sin bypass admin en links; motor ⏳), validations ✅. Siguiente: TransactionRuleCondition.                                                                                                                                                                                         |
| 2026-07-11 | **TransactionRuleCondition plan:** `transactionRule` immutable after create — **reparent same-owner removed**. Domain validations planned: field/operator matrices, duplicate guard, DELETE last condition → deactivate parent rule.                                                                                                                                         |
| 2026-07-11 | **TransactionRuleCondition complete:** immutable parent, field/operator/value matrices, normalized duplicate guard, owner-scoped ACCOUNT ids, presence-aware PATCH null handling, and last-condition rule deactivation. Later hardened `position` as server-managed append order.                                                                                            |
| 2026-07-09 | TransactionRuleCondition: ownership ✅ (pattern C via `transactionRule`), validations ✅ (parent required, ~~reparent same-owner~~ → **immutable parent**). Domain rules 🟡 (field/operator/value, delete side-effect). Siguiente: implement TRC domain rules.                                                                                                               |
| 2026-07-09 | CreditAccountDetails: ownership ✅ (pattern B via `account`), validations ✅ (CREDIT_CARD only, immutable account, duplicate guard). Domain rules 🟡. Siguiente: ApiAccessToken.                                                                                                                                                                                             |
| 2026-07-09 | ApiAccessToken: ownership ✅ (pattern A), security baseline ✅ (no `tokenHash` in reads, immutable secrets). Domain rules 🟡. Siguiente: ApiAccessTokenPermission.                                                                                                                                                                                                           |
| 2026-07-09 | ApiAccessTokenPermission: ownership ✅ (pattern C via `apiAccessToken`), validations ✅ (immutable parent/grant, server `createdAt`, duplicate guard). Domain rules 🟡. Siguiente: UserDashboardPreference o InternalTransfer.                                                                                                                                               |
| 2026-07-09 | UserDashboardPreference: ownership ✅ (pattern A + 1:1 guard), validations ✅ (existsByUserId, PATCH user preserve/null). Domain rules 🟡. Siguiente: InternalTransfer.                                                                                                                                                                                                      |
| 2026-07-11 | InternalTransfer domain rules ✅: origin unrestricted, notes normalization, strict createdAt/link PATCH semantics, no duplicate participation in either role, FT delete cleanup, candidate endpoints aligned. Balances/atomic create remain out of scope.                                                                                                                    |
| 2026-07-09 | TransactionIngestion: modelo refactor ✅ — `account` ManyToOne required (reemplaza M2M `accounts`); pattern planificado **B** (no D). Ownership ⏳. Siguiente: ownership PR (14/17).                                                                                                                                                                                         |
| 2026-07-09 | TransactionIngestion: ownership ✅ (pattern B via `account`), validations ✅ (inmutables, server defaults, scoped helpers). Domain rules ⏳ (pipeline). Siguiente: FileIngestion.                                                                                                                                                                                            |
| 2026-07-12 | TransactionIngestion domain rules ✅: lifecycle/status transitions, counter consistency, server-owned timestamps, source/error normalization, final child metadata guards, and explicit revert/delete cleanup order.                                                                                                                                                         |
| 2026-07-11 | FileIngestion domain rules ✅: normalization, immutable file metadata, mutable statement dates with range guard, server-owned `createdAt`, direct delete blocked, and TransactionIngestion parent cleanup.                                                                                                                                                                   |
| 2026-07-09 | ApiIngestion: ownership ✅ (pattern C + dual parent + same-owner), validations ✅ (parent API, 1:1 guard, `requestId` unique global, immutable parents/token/requestId/timestamps, server `createdAt`/`receivedAt`). Siguiente: IngestionRecord.                                                                                                                             |
| 2026-07-09 | IngestionRecord: ownership ✅ (pattern C + optional FT + same-owner), validations ✅ (immutable parents/recordIndex/createdAt, FT 1:1 guard, recordIndex unique, server `createdAt`, FT helper scoped). **17/17 ownership complete.** Superseded by 2026-07-12 domain-rule pass.                                                                                             |
| 2026-07-12 | IngestionRecord domain rules ✅: status consistency, parent final freeze, externalRecordId normalization/uniqueness, rawData immutability/log safety, direct delete blocked. 87 IT + 7 service + 1 FT helper IT. Pipeline/count reconciliation ⏳ fase 6.                                                                                                                    |
| 2026-07-12 | FinancialTransaction domain rules ✅: JsonNode POST/PUT/PATCH, server timestamps, immutable account/origin/ingestion, owner-scoped links, category/subscription compatibility, internal-transfer guards, delete cleanup for IngestionRecord/InternalTransfer/tag joins. 101 IT + 10 service.                                                                                 |
| 2026-07-12 | TransactionRule CRUD/domain baseline ✅: strict server-owned `createdAt`/`updatedAt`, PUT documented as full DTO update except server-managed priority preservation, PATCH remains JsonNode presence-aware, delete cleanup direct via repositories. Rule engine and batch reclassification remain deferred; manual Move up / Move down reorder added later.                  |
| 2026-07-13 | FinancialAccount balance read model ✅: backend-only `GET /api/financial-accounts/{id}/balance`; strategy calculators by `AccountType`; uses `transactionDate` from `initialBalanceDate` through `asOfDate`; DEBIT/CASH/INVESTMENT return `currentBalance`; CREDIT_CARD returns `currentDebt`/`availableCredit` and `missingCreditDetails`. No persisted balances/UI/charts. |
| 2026-07-13 | FinancialAccount monetary scale ✅: `initialBalance` required, positive/zero/negative allowed, service rejects `scale > 2` without rounding on create/update/patch. 138 IT + 24 service. Superseded for balance status by the 2026-07-13 balance read model entry.                                                                                                           |
| 2026-07-12 | FinancialAccount domain rules ✅: final orchestrator delete implemented (TI tree → remaining FT → budget links → subscription account null → CAD → account), `initialBalanceDate` floor vs earliest transactionDate, `active=false` no side effects. 118 IT + 12 service. Superseded for balance status by the 2026-07-13 balance read model entry.                          |
| 2026-07-10 | Added [`VALIDATIONS.md`](VALIDATIONS.md) — per-entity validation catalog across DTO, entity, DB, service, and REST layers.                                                                                                                                                                                                                                                   |
| 2026-07-10 | **Validation hardening (Tag + FinancialAccount):** Tag `name` unique per owner (trim + case-insensitive); FA `currency`/`accountType` immutable; FA PATCH JsonNode; `400 invalid` mapping. 200 tests (69+12 Tag, 108+11 FA). Docs: TESTING, VALIDATIONS, IMPLEMENTATION.                                                                                                     |
| 2026-07-10 | **Budget PATCH JsonNode:** M2M link semantics (`accounts`/`categories`/`tags` absent preserves, `null`/`[]` clears, ids replace). 122 tests (111+14 Budget).                                                                                                                                                                                                                 |
| 2026-07-10 | **Category name uniqueness (hierarchical):** sibling-unique `name` per owner + `categoryType` + `parentCategory` (trim, case-insensitive); PATCH revalidates on `name`/`categoryType`/`parentCategory` change. 98 tests (84+14 Category).                                                                                                                                    |
| 2026-07-10 | Added [`DOMAIN-RULES.md`](DOMAIN-RULES.md) — domain rules catalog (delete guards, balances, budget matching, rule motor, ingestion pipeline) with Fase 4/6 implementation order.                                                                                                                                                                                             |
| 2026-07-11 | [`DOMAIN-RULES.md`](DOMAIN-RULES.md) refocused: FA-only detail with Done/Proposed/Open per rule; other entities deferred.                                                                                                                                                                                                                                                    |
| 2026-07-11 | FA DELETE spec: ordered domain cascade (budgets unlink, subscriptions null, credit details, transfers, ingestion tree, txs, account); UPDATE `initialBalanceDate` floor proposed.                                                                                                                                                                                            |
| 2026-07-11 | FA DELETE architecture plan: orchestration in `FinancialAccountService`; delegate `deleteAllForAccount` to `FinancialTransactionService` + `TransactionIngestionService`; single transaction. Superseded by 2026-07-12 implementation entry.                                                                                                                                 |
| 2026-07-11 | [`DOMAIN-RULES.md`](DOMAIN-RULES.md) full plan: 17 entities in 3 groups (simple→complex); FA last; **next implement:** UserDashboardPreference.                                                                                                                                                                                                                              |
| 2026-07-11 | UserDashboardPreference domain rules: DELETE simple; `configuration` required + parseable JSON (`{}` OK); schema/`/me`/upsert deferred.                                                                                                                                                                                                                                      |
| 2026-07-11 | ApiAccessTokenPermission domain rules ✅: confirmatory ITs (DELETE preserves token/sibling; CREATE on REVOKED/EXPIRED; same permission different token). Service unchanged. 37 IT.                                                                                                                                                                                           |
| 2026-07-11 | Category domain rules ✅: block delete with children; leaf delete+cleanup (FT/FS/budget/rule); parentCategory immutable; categoryType guard; child type matches parent. 103 IT + 16 service. Default categories on signup deferred.                                                                                                                                          |
| 2026-07-11 | CreditAccountDetails domain rules ✅: direct DELETE blocked (`400` invalid; admin no bypass; foreign `404`); mutable credit fields without utilization checks; `CREDIT_CARD` details expected for full functionality but not enforced by `FinancialAccountService` today (atomic create / required-details enforcement deferred). 41 IT + 10 service.                        |
| 2026-07-13 | CreditAccountDetails timestamp hardening ✅: `createdAt`/`updatedAt` server-owned; create ignores/missing client timestamps; PUT/PATCH reject explicit null/change and set `updatedAt=now` on success; frontend create no longer injects fake timestamps. 50 IT + 21 service + frontend CAD tests.                                                                           |
| 2026-07-13 | FinancialAccount/CreditAccountDetails UI composition ✅: `CREDIT_CARD` FinancialAccount create/edit embeds editable credit-card details without parent selector; detail embeds read-only section; standalone CAD CRUD remains; backend adds scoped `GET /api/credit-account-details/by-account/{accountId}` helper. 53 CAD IT + 22 CAD service + FA/CAD frontend tests.      |
| 2026-07-17 | CSV Ingestion I1A/I1B backend ✅: canonical CSV parser/validator + `POST /api/transaction-ingestions/file-preview`; persists `TransactionIngestion`, `FileIngestion`, and preview `IngestionRecord`s only; no DB/JDL/Liquibase, no `FinancialTransaction` creation, no Rule Engine.                                                                                          |
| 2026-07-17 | CSV Ingestion I1C frontend ✅: TransactionIngestion “New File Import” workflow at `/transaction-ingestion/file-preview/new`; account + CSV upload, persisted preview summary/warnings/rows, preview-only notice, no confirm/import action.                                                                                                                                   |
| 2026-07-17 | CSV Ingestion I2A status lifecycle ✅: `IngestionRecordStatus.CREATED` removed; preview rows now use `VALID`; `IMPORTED` reserved for confirm import; data migration updates existing `CREATED` rows to `VALID`; no TransactionIngestion status change and no confirm import yet.                                                                                            |
| 2026-07-11 | Grupo 1 delete confirmation dialogs ✅: domain-aware UX copy for UDP, AATP, Tag, Category; CAD informational-only (no confirm). i18n en/es.                                                                                                                                                                                                                                  |
| 2026-07-11 | **Decision 11C — snapshot audit plan:** remove required `ApiIngestion`→`ApiAccessToken` FK; add snapshot fields; token DELETE allowed with historical ingestions; cascade permissions only. Superseded by implementation entry below.                                                                                                                                        |
| 2026-07-11 | **Decision 11C implemented ✅:** snapshot fields + Liquibase `20260711160000`; token server-side generation + `rawToken` reveal modal; delete cascades permissions only; `SpaWebFilter` fix for `/api-*` frontend routes; ITs + service tests. Docs synced. Runtime API auth enforcement deferred fase 6.                                                                    |
