# FinTrack — Domain rules catalog

Living reference for **business rules** — delete cascades, update guards, product semantics — separate from ownership and shape validations.

Companion docs:

- [`docs/IMPLEMENTATION.md`](IMPLEMENTATION.md) — master tracker
- [`docs/VALIDATIONS.md`](VALIDATIONS.md) — shape / immutables / uniqueness (not lifecycle)
- [`docs/OWNERSHIP-FLOWS.md`](OWNERSHIP-FLOWS.md) — user vs admin access
- [`docs/TESTING.md`](TESTING.md) — tests

**Principle:** domain rules live in the **service**. `IllegalArgumentException` → REST `400`, `message = error.invalid`, `params = <entityName>`.

**Workflow:** document all rules here (**Proposed** / **Open**) → implement simple → complex → set **Done** + tests.

---

## Status values

| Status       | Meaning                                             |
| ------------ | --------------------------------------------------- |
| **Done**     | Implemented + tested                                |
| **Proposed** | Agreed spec — not implemented                       |
| **Open**     | Needs product/tech decision                         |
| **Blocked**  | Depends on another entity's rules                   |
| **Deferred** | Agreed out of scope for current implementation pass |

---

## Implementation roadmap (simple → complex)

Implement and mark **Done** in this order. **Do not** implement `FinancialAccount` delete orchestration until Grupo 3 ingestion + transaction delete rules are **Done**.

### Grupo 1 — Flat / few dependencies

| #   | Entity                      | Why start here                        |
| --- | --------------------------- | ------------------------------------- | ------------- | ----------------- | -------- |
| 1   | **UserDashboardPreference** | 1:1 user, no FK dependents            | **Done**      |
| 2   | ApiAccessTokenPermission    | Pattern C; immutable parent           | **Done**      |
| 3   | CreditAccountDetails        | Pattern B; 1:1 account                | **Done**      |
| 4   | Tag                         | M2M unlink + delete                   | Uniqueness ✅ | `active` soft-off | **Done** |
| 5   | Category                    | Pattern A; hierarchy + delete cleanup | **Done**      |
| 6   | FinancialSubscription       | Unlink FT/rules + delete              | **Done**      |

### Grupo 2 — Links, not ledger core

| #   | Entity                   |
| --- | ------------------------ |
| 6   | FinancialSubscription    |
| 7   | Budget                   |
| 8   | TransactionRuleCondition |
| 9   | TransactionRule          |
| 10  | ApiAccessToken           |

### Grupo 3 — Core financial + orchestration

| #   | Entity               |
| --- | -------------------- | ----------------------- |
| 11  | InternalTransfer     |
| 12  | FinancialTransaction |
| 13  | TransactionIngestion |
| 14  | FileIngestion        |
| 15  | ApiIngestion         |
| 16  | IngestionRecord      |
| 17  | **FinancialAccount** | Orchestrator — **last** |

---

## Master summary

| #   | Entity                   | DELETE                                | UPDATE guards                                                                     | Product rules                                         | Overall                                   |
| --- | ------------------------ | ------------------------------------- | --------------------------------------------------------------------------------- | ----------------------------------------------------- | ----------------------------------------- |
| 1   | UserDashboardPreference  | Simple                                | 1:1 + JSON parse                                                                  | Schema, /me, upsert                                   | **Done**                                  |
| 2   | ApiAccessTokenPermission | Simple                                | Immutables ✅                                                                     | Runtime enforcement                                   | **Done**                                  |
| 3   | CreditAccountDetails     | Simple / FA cascade                   | Immutables ✅                                                                     | FA UI composition ✅; atomic backend command deferred | **Done**                                  |
| 4   | Tag                      | M2M unlink + delete                   | Uniqueness ✅                                                                     | `active` soft-off                                     | **Done**                                  |
| 5   | Category                 | Block if children; leaf unlink+delete | Immutability ✅                                                                   | Default cats on signup                                | **Done**                                  |
| 6   | FinancialSubscription    | Unlink FT/rules + delete              | Links ✅ + dates + structural                                                     | Import matching                                       | **Done**                                  |
| 7   | Budget                   | Unlink M2M + delete                   | Links ✅ + validations                                                            | Empty-set matching, spend                             | **Done**                                  |
| 8   | TransactionRuleCondition | Via parent                            | Parent immutable + validations                                                    | Motor eval                                            | **Done**                                  |
| 9   | TransactionRule          | Condition/tag cleanup ✅              | Outputs/normalization/owner links/timestamps ✅                                   | Motor on FT create                                    | **Done**                                  |
| 10  | ApiAccessToken           | Simple + cascade permissions          | Secrets ✅ + reveal-once                                                          | Revocation runtime / expiry enforcement (fase 6)      | **Done** (11C)                            |
| 11  | InternalTransfer         | Simple                                | Pair/lifecycle ✅                                                                 | Balances, atomic create                               | **Done**                                  |
| 12  | FinancialTransaction     | `deleteAllForAccount`                 | Lifecycle + links + delete cleanup ✅                                             | Rule motor, balances                                  | **Done**                                  |
| 13  | TransactionIngestion     | `deleteAllForAccount`                 | Lifecycle + revert delete ✅                                                      | Runtime pipeline/idempotency                          | **Done**                                  |
| 14  | FileIngestion            | Via TI service                        | Parent ✅ + metadata immutability + direct delete blocked                         | Upload / parser (fase 6)                              | **Done** (metadata lifecycle)             |
| 15  | ApiIngestion             | Via TI service                        | Parent ✅ + token snapshots (11C) + direct delete blocked                         | API runtime handler + idempotency (fase 6)            | **Done** (11C lifecycle; runtime pending) |
| 16  | IngestionRecord          | Via TI service                        | Immutables ✅ + status consistency ✅                                             | Pipeline/count reconciliation                         | **Done**                                  |
| 17  | FinancialAccount         | Orchestrated                          | Currency/type ✅ + delete orchestration + initial date floor + balance read model | UI/dashboard balance display                          | **Done**                                  |

---

## Architecture — cross-service delete (Grupo 3 only)

| Service                         | Role                                                                               |
| ------------------------------- | ---------------------------------------------------------------------------------- |
| **FinancialAccountService**     | Orchestrates account delete — **does not** touch ingestion child entities directly |
| **FinancialTransactionService** | `deleteAllForAccount(account)` — txs + internal transfer legs                      |
| **TransactionIngestionService** | `deleteAllForAccount(account)` — ingestion tree (records, file/api, ingestion)     |

Single `@Transactional` on `FinancialAccountService.delete()` — rollback on any failure.

---

# Grupo 1

## 1. UserDashboardPreference

**Pattern:** A — direct `user`. **Relationships:** none (1 logical row per user).

### DELETE

| Rule          | Decision                                        | Applies to admin | Error                   | Status   |
| ------------- | ----------------------------------------------- | ---------------- | ----------------------- | -------- |
| Simple delete | Deletes **only** the preference row; no cascade | Yes              | `404` if not accessible | **Done** |

### CREATE / UPDATE / PATCH — `configuration`

| Rule                                    | Decision                                            | Applies to admin | Error                                                  | Status       |
| --------------------------------------- | --------------------------------------------------- | ---------------- | ------------------------------------------------------ | ------------ |
| `configuration` required (business)     | Reject `null` or blank/whitespace-only              | Yes              | `400` `error.invalid` `params=userDashboardPreference` | **Done**     |
| `configuration` parseable JSON          | Must parse as JSON object/array (parser in service) | Yes              | `400` invalid                                          | **Done**     |
| `"{}"` is valid                         | Empty JSON object allowed                           | Yes              | —                                                      | **Done**     |
| No schema validation                    | Structure/widgets not validated yet                 | —                | —                                                      | **Deferred** |
| `configuration` mutable on update/patch | Business-editable field                             | Yes              | —                                                      | **Done**     |
| PATCH `configuration: null`             | Reject (same as blank)                              | Yes              | `400` invalid                                          | **Done**     |
| PATCH omit `configuration`              | Preserve existing value; skip validation            | Yes              | —                                                      | **Done**     |

**Implementation:** `validateConfiguration()` in `UserDashboardPreferenceService` on `save` / `update` / `partialUpdate` when `configuration` is present; `IllegalArgumentException` mapped in resource (POST/PUT/PATCH).

### Already **Done** (ownership / validations baseline)

| Rule                              | Implementation                              |
| --------------------------------- | ------------------------------------------- |
| One preference per user on create | `existsByUserId()`                          |
| Owner immutable                   | Preserve `user`; PATCH `user: null` → `400` |
| PATCH `user` JsonNode semantics   | Absent preserves owner                      |

### Deferred (not this implementation)

| Rule                                  | Notes                                         |
| ------------------------------------- | --------------------------------------------- |
| JSON schema validation                | Widget/layout structure — future product pass |
| `GET /me` or upsert endpoint          | No `/me` yet                                  |
| Auto-create default on dashboard load | No implicit create                            |
| Deep merge on PATCH                   | Full replace of `configuration` string only   |
| `UNIQUE(user_id)` Liquibase           | Service guard only today                      |

### Tests — **Done**

| Test                                                              | Layer | Status   |
| ----------------------------------------------------------------- | ----- | -------- |
| `null` / blank / invalid JSON / JSON primitive config → exception | Unit  | **Done** |
| PATCH `configuration: null` → exception; omit field → OK          | Unit  | **Done** |
| POST/PUT/PATCH bad config → `400` invalid                         | IT    | **Done** |
| PATCH JSON array `[]` allowed                                     | IT    | **Done** |
| DELETE removes row only; scoped `404`                             | IT    | **Done** |

---

## 2. ApiAccessTokenPermission

**Pattern:** C — via `apiAccessToken.user`. **Relationships:** parent `ApiAccessToken` only (no children).

**Baseline:** ownership ✅, validations ✅ (immutable parent/grant/`createdAt`, duplicate guard). **Likely no service code changes** — confirm behavior + add missing ITs.

### DELETE

| Rule                                | Decision                                   | Applies to admin | Error                   | Status   |
| ----------------------------------- | ------------------------------------------ | ---------------- | ----------------------- | -------- |
| Simple delete                       | Deletes **only** the permission row        | Yes              | `404` if not accessible | **Done** |
| Parent `ApiAccessToken` survives    | Token row not deleted                      | Yes              | —                       | **Done** |
| Sibling permissions survive         | Other `(token, permission)` rows unchanged | Yes              | —                       | **Done** |
| `ApiIngestion` / historical records | Unaffected by permission delete            | Yes              | —                       | **Done** |
| Last permission on token            | Allowed — token may have zero permissions  | Yes              | —                       | **Done** |

### CREATE / UPDATE / PATCH

| Rule                                     | Decision                                                     | Applies to admin | Error         | Status   |
| ---------------------------------------- | ------------------------------------------------------------ | ---------------- | ------------- | -------- |
| `apiAccessToken` required on create      | Resolved via `findAccessibleApiAccessTokenEntity()`          | Yes              | `400` invalid | **Done** |
| No reparenting                           | `apiAccessToken` immutable after create                      | Yes              | `400` invalid | **Done** |
| `permission` immutable                   | Change = delete old row + create new                         | Yes              | `400` invalid | **Done** |
| `createdAt` server-assigned + immutable  | `Instant.now()` on create only                               | Yes              | `400` invalid | **Done** |
| Duplicate `(apiAccessToken, permission)` | Block on create                                              | Yes              | `400` invalid | **Done** |
| Same `permission` on **different** token | Allowed                                                      | Yes              | —             | **Done** |
| CRUD when parent token REVOKED/EXPIRED   | **No** `token.status` / expiration guards on permission CRUD | Yes              | —             | **Done** |
| PATCH `apiAccessToken` absent            | Preserves parent                                             | Yes              | —             | **Done** |
| PATCH `apiAccessToken: null`             | Reject                                                       | Yes              | `400` invalid | **Done** |

**Implementation:** `ApiAccessTokenPermissionService` — `rejectApiAccessTokenChange`, `rejectPermissionChange`, `rejectCreatedAtChange`, `existsByApiAccessTokenIdAndPermission`.

### Deferred (not this pass)

| Rule                                | Notes                                                           |
| ----------------------------------- | --------------------------------------------------------------- |
| Runtime permission enforcement      | API auth, token lookup, endpoint checks, rate limiting — Fase 6 |
| Token delete cascade to permissions | Owned by `ApiAccessToken` domain rules (#10)                    |

### Tests — **Done**

| Test                                                            | Layer | Status   |
| --------------------------------------------------------------- | ----- | -------- |
| DELETE removes permission only; token still exists              | IT    | **Done** |
| DELETE one permission; sibling permission on same token remains | IT    | **Done** |
| CREATE permission on REVOKED/EXPIRED token succeeds             | IT    | **Done** |
| Duplicate `(token, permission)` → `400`                         | IT    | **Done** |
| Same permission on different token → `201`                      | IT    | **Done** |
| Immutable parent/grant/`createdAt` on PUT/PATCH                 | IT    | **Done** |
| DELETE scoped `404` cross-user                                  | IT    | **Done** |

Grupo 1 #2 domain rules **Done**.

---

## 3. CreditAccountDetails

**Pattern:** B — via `account`. **Relationships:** 1:1 `FinancialAccount`.

### DELETE

| Rule                          | Decision                                                  | Applies to admin | Error                                               | Status                                    |
| ----------------------------- | --------------------------------------------------------- | ---------------- | --------------------------------------------------- | ----------------------------------------- |
| Not independently deletable   | Direct `DELETE /api/credit-account-details/{id}` blocked  | Yes              | `400` `error.invalid` `params=creditAccountDetails` | **Done**                                  |
| Ownership before domain error | Foreign/inaccessible row → `404` first                    | Yes              | `404`                                               | **Done**                                  |
| Deleted by FA orchestration   | `FinancialAccountService.delete()` step 6 removes details | Yes              | —                                                   | **Proposed** (FA #17; not in CAD service) |

**Implementation:** `CreditAccountDetailsService.delete()` — `findAccessibleEntity()` then `IllegalArgumentException`; no `deleteById` in CAD service. Cascade belongs to FA service only.

### CREATE

| Rule                                                               | Decision                                                                                              | Applies to admin | Error         | Status         |
| ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------- | ---------------- | ------------- | -------------- |
| Parent `account` required                                          | Resolved via accessible account                                                                       | Yes              | `400` invalid | **Done**       |
| Only `CREDIT_CARD` accounts                                        | On create                                                                                             | Yes              | `400` invalid | **Done**       |
| One details per account                                            | `existsByAccountId()` on create                                                                       | Yes              | `400` invalid | **Done**       |
| `createdAt` / `updatedAt` server-owned                             | Ignore client values; set both to `Instant.now()`                                                     | Yes              | —             | **Done**       |
| `CREDIT_CARD` expected to have details for full card functionality | Future/full-functionality expectation; separate CRUD today; not enforced by `FinancialAccountService` | Yes              | —             | **Documented** |

**Future:** atomic endpoint may create `FinancialAccount` + `CreditAccountDetails` together for `CREDIT_CARD`, or a later guard may require details before enabling full credit-card features. Do not break standalone FA create in this pass.

### UPDATE / PATCH

| Rule                                  | Decision                                                                                                      | Applies to admin | Error         | Status   |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ---------------- | ------------- | -------- |
| Parent `account` immutable            | After create                                                                                                  | Yes              | `400` invalid | **Done** |
| `creditLimit` mutable                 | `>= 0`; informational only                                                                                    | Yes              | —             | **Done** |
| Lower limit below outstanding balance | Allowed — no utilization check                                                                                | Yes              | —             | **Done** |
| `statementDay` mutable                | `1–31`                                                                                                        | Yes              | —             | **Done** |
| `paymentDueDay` mutable               | `1–31`; no `paymentDueDay > statementDay` rule                                                                | Yes              | —             | **Done** |
| `annualInterestRate` mutable          | `>= 0`; no interest calculations yet                                                                          | Yes              | —             | **Done** |
| `createdAt` server-owned              | PUT/PATCH preserve existing; explicit null or changed value rejected; same value allowed as no-op             | Yes              | `400` invalid | **Done** |
| `updatedAt` server-owned              | Explicit null or changed value rejected; same value allowed, then successful PUT/PATCH sets `updatedAt = now` | Yes              | `400` invalid | **Done** |

### Product rules (deferred)

| Rule                                   | Notes        |
| -------------------------------------- | ------------ |
| Interest / statement calculations      | Out of scope |
| Credit utilization warnings            | Out of scope |
| Blocking transactions by `creditLimit` | Out of scope |

### Tests — **Done**

| Test                                                                                  | Layer | Status   |
| ------------------------------------------------------------------------------------- | ----- | -------- |
| Own direct DELETE → `400` invalid `params=creditAccountDetails`                       | IT    | **Done** |
| Admin foreign DELETE → `400` invalid                                                  | IT    | **Done** |
| Foreign user DELETE → `404`                                                           | IT    | **Done** |
| PATCH `creditLimit` / `statementDay` / `paymentDueDay` / `annualInterestRate` allowed | IT    | **Done** |
| Lower `creditLimit` without utilization block                                         | IT    | **Done** |
| Accessible direct delete throws in service                                            | Unit  | **Done** |

---

## 4. Tag

**Pattern:** A — direct `user`. **Relationships:** M2M inverse — `FinancialTransaction.tags`, `TransactionRule.resultingTags`, `FinancialSubscription.tags`, `Budget.tags`.  
**Status:** **Done** (ownership + validations + server-owned timestamps + CRUD UX cleanup + DELETE M2M unlink).

### Baseline **Done** (ownership + validations)

| Rule                                | Implementation                                            |
| ----------------------------------- | --------------------------------------------------------- |
| Pattern A ownership                 | `findAccessibleEntity()`; admin bypass                    |
| `name` trim + per-owner uniqueness  | Case/trim-insensitive `existsByUserIdAndNormalizedName()` |
| Uniqueness vs tag owner (not actor) | Admin edits foreign tag validated against foreign owner   |
| REST `IllegalArgumentException`     | `400` `error.invalid` `params=tag`                        |

### CREATE / UPDATE / PATCH

| Rule                             | Decision                                                                                  | Status   |
| -------------------------------- | ----------------------------------------------------------------------------------------- | -------- |
| Create/edit UX fields            | Tag form exposes only `name`, `description`, `color`, `active`                            | **Done** |
| Relationship editors             | Not shown in Tag create/edit; manage links from owning flows/entities                     | **Done** |
| Detail/list relationship display | Do not show raw relationship IDs; related read-only lists are deferred                    | **Done** |
| `createdAt` / `updatedAt`        | Server-owned                                                                              | **Done** |
| CREATE timestamps                | Client timestamps ignored; service sets both to `now`                                     | **Done** |
| PUT timestamps                   | Same timestamp values allowed; changed/null timestamps rejected; `updatedAt` reset to now | **Done** |
| PATCH timestamps                 | `JsonNode` presence-aware for timestamps; omitted preserves, changed/null rejected        | **Done** |
| Successful PUT/PATCH             | Preserve `createdAt`; set `updatedAt = now`                                               | **Done** |
| Ownership                        | Client cannot change `user`; create assigns current user; update/patch preserve owner     | **Done** |

### DELETE

| Rule                                   | Decision                                        | Applies to admin | Error | Status   |
| -------------------------------------- | ----------------------------------------------- | ---------------- | ----- | -------- |
| Delete allowed when in use             | **Yes** — unlink first, then delete tag row     | Yes              | —     | **Done** |
| Unlink `FinancialTransaction.tags`     | Remove join rows only                           | Yes              | —     | **Done** |
| Unlink `TransactionRule.resultingTags` | Remove join rows only                           | Yes              | —     | **Done** |
| Unlink `FinancialSubscription.tags`    | Remove join rows only                           | Yes              | —     | **Done** |
| Unlink `Budget.tags`                   | Remove join rows only                           | Yes              | —     | **Done** |
| Do **not** delete related entities     | FT, rules, subscriptions, budgets survive       | Yes              | —     | **Done** |
| Single transaction                     | Cleanup + `deleteById` in `TagService.delete()` | Yes              | —     | **Done** |
| Foreign user DELETE                    | `404` (ownership first)                         | —                | `404` | **Done** |
| Accessible / admin DELETE              | `204` after cleanup                             | Yes              | —     | **Done** |
| No soft delete                         | Hard delete tag row                             | Yes              | —     | **Done** |

**Join tables to clean (by `tags_id`):**

| Table                                  | Owning entity                           |
| -------------------------------------- | --------------------------------------- |
| `rel_financial_transaction__tags`      | `FinancialTransaction`                  |
| `rel_transaction_rule__resulting_tags` | `TransactionRule` (`resulting_tags_id`) |
| `rel_financial_subscription__tags`     | `FinancialSubscription`                 |
| `rel_budget__tags`                     | `Budget`                                |

**Implementation:** explicit `@Modifying` repository deletes (or equivalent) on the four join tables by `tagId`, then `tagRepository.deleteById(id)`. Do **not** cascade-delete parent entities. Do **not** rely on accidental JPA orphan behavior.

### UPDATE / PATCH

| Rule                                                | Decision                                                | Applies to admin | Error         | Status   |
| --------------------------------------------------- | ------------------------------------------------------- | ---------------- | ------------- | -------- |
| `name` / `color` / `description` / `active` mutable | Allowed                                                 | Yes              | —             | **Done** |
| `name` uniqueness                                   | Trim + case-insensitive per owner                       | Yes              | `400` invalid | **Done** |
| Inactive tags in uniqueness                         | `active=false` still blocks duplicate normalized `name` | Yes              | `400` invalid | **Done** |
| Update used tag                                     | Allowed if uniqueness holds; links unchanged            | Yes              | —             | **Done** |

### Product rules

| Rule                                      | Decision                                           | Status       |
| ----------------------------------------- | -------------------------------------------------- | ------------ |
| `active=false`                            | Hide/deactivate without deleting; **links remain** | **Done**     |
| `active=false` not required before delete | User may hard-delete a used tag                    | **Done**     |
| Unlink audit / event log                  | Out of scope                                       | **Deferred** |

### Contrasts (intentional)

| Entity       | DELETE when linked                              |
| ------------ | ----------------------------------------------- |
| **Tag**      | Unlink M2M → delete tag                         |
| **Category** | Block if children; leaf unlink+cleanup → delete |

### Tests

| Test                                                                         | Layer        | Status   |
| ---------------------------------------------------------------------------- | ------------ | -------- |
| Delete unused tag → `204`                                                    | IT           | **Done** |
| Delete tag on FT → unlinked; FT survives                                     | IT           | **Done** |
| Delete tag on `TransactionRule.resultingTags` → unlinked; rule survives      | IT           | **Done** |
| Delete tag on `FinancialSubscription.tags` → unlinked; subscription survives | IT           | **Done** |
| Delete tag on `Budget.tags` → unlinked; budget survives                      | IT           | **Done** |
| Delete tag linked in multiple entity types → all cleaned                     | IT           | **Done** |
| Admin delete foreign used tag → `204` + cleanup                              | IT           | **Done** |
| Foreign user DELETE → `404`                                                  | IT           | **Done** |
| Update/patch used tag fields (uniqueness OK)                                 | IT           | **Done** |
| Inactive tag blocks duplicate normalized name                                | IT           | **Done** |
| Join-table cleanup methods                                                   | Service unit | **Done** |

---

## 5. Category

**Pattern:** A + self-FK `parentCategory`. **Relationships:** direct children, `FinancialTransaction.category`, `TransactionRule.resultingCategory`, `FinancialSubscription.category`, `Budget.categories` M2M.  
**Status:** **Done** (ownership + validations + domain rules). No `Category.system` in v1.

### Product context (v1)

| Rule                     | Decision                                                            | Status                                                  |
| ------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------- |
| Always user-owned        | No global/shared categories                                         | **Done**                                                |
| Default categories       | Created per user on signup/onboarding as normal rows                | **Deferred** (separate pass — not in `CategoryService`) |
| No protected/system flag | User may rename/update/deactivate/delete defaults like any category | **Done** (by omission)                                  |

### DELETE

| Rule                                   | Decision                                   | Applies to admin | Error         | Status   |
| -------------------------------------- | ------------------------------------------ | ---------------- | ------------- | -------- |
| Block if direct child categories exist | Active **and** inactive children block     | Yes              | `400` invalid | **Done** |
| Leaf delete allowed when referenced    | Cleanup first, then `deleteById`           | Yes              | —             | **Done** |
| Guard order                            | Access → children check → cleanup → delete | Yes              | —             | **Done** |
| `FinancialTransaction.category`        | Set `null`                                 | Yes              | —             | **Done** |
| `FinancialSubscription.category`       | Set `null`                                 | Yes              | —             | **Done** |
| `Budget.categories`                    | Remove join rows only                      | Yes              | —             | **Done** |
| `TransactionRule.resultingCategory`    | Set `null` + `active = false`              | Yes              | —             | **Done** |
| Do **not** delete related entities     | FT, subscriptions, budgets, rules survive  | Yes              | —             | **Done** |
| Foreign user DELETE                    | `404` (ownership first)                    | —                | `404`         | **Done** |
| Accessible / admin DELETE              | `204` after cleanup (leaf only)            | Yes              | —             | **Done** |

**Implementation:** `@Modifying` cleanup on FK/M2M (`flushAutomatically` + `clearAutomatically`), then `deleteById`. Single transaction in `CategoryService.delete()`.

### UPDATE / PATCH

| Rule                                                | Decision                                                      | Applies to admin | Error                                  | Status   |
| --------------------------------------------------- | ------------------------------------------------------------- | ---------------- | -------------------------------------- | -------- |
| `parentCategory` immutable after create             | No move/merge                                                 | Yes              | `400` invalid                          | **Done** |
| PATCH omit `parentCategory`                         | Preserves existing parent                                     | Yes              | —                                      | **Done** |
| Same `parentCategory` / no-op                       | Allowed                                                       | Yes              | —                                      | **Done** |
| `categoryType` mutable only if unused               | See “in use” below                                            | Yes              | `400` invalid                          | **Done** |
| Child `categoryType` == parent                      | On create; on type change when has parent                     | Yes              | `400` invalid                          | **Done** |
| `name` / `color` / `description` / `active` mutable | Sibling uniqueness applies                                    | Yes              | `400` invalid                          | **Done** |
| Inactive in uniqueness                              | `active=false` still blocks duplicate sibling name            | Yes              | `400` invalid                          | **Done** |
| Sibling-unique `name`                               | See VALIDATIONS.md                                            | Yes              | `400` invalid                          | **Done** |
| `createdAt` server-owned                            | Preserve existing on PUT/PATCH                                | Yes              | `400` invalid if explicit null/changed | **Done** |
| `updatedAt` server-owned                            | Same timestamp allowed as no-op; successful update sets `now` | Yes              | `400` invalid if explicit null/changed | **Done** |

**Category is “in use” for `categoryType` change if:** direct children; `FinancialTransaction.category`; `FinancialSubscription.category`; `Budget.categories`; `TransactionRule.resultingCategory`.

### CREATE

| Rule                                     | Decision                                          | Status   |
| ---------------------------------------- | ------------------------------------------------- | -------- |
| `parentCategory` accessible + same owner |                                                   | **Done** |
| No self-parent                           |                                                   | **Done** |
| Child `categoryType` matches parent      |                                                   | **Done** |
| Sibling-scope uniqueness                 |                                                   | **Done** |
| `createdAt` / `updatedAt` server-owned   | Client values ignored; service sets both to `now` | **Done** |

### Contrasts (intentional)

| Entity       | DELETE when linked                                  |
| ------------ | --------------------------------------------------- |
| **Tag**      | Always unlink M2M → delete tag                      |
| **Category** | Block if has children; leaf unlink/cleanup → delete |

### Tests

| Test                                                            | Layer        | Status   |
| --------------------------------------------------------------- | ------------ | -------- |
| Delete with direct active/inactive child → `400`                | IT           | **Done** |
| Delete unused leaf → `204`                                      | IT           | **Done** |
| Delete leaf used by FT / subscription / budget / rule → cleanup | IT           | **Done** |
| Delete leaf used in multiple entity types                       | IT           | **Done** |
| Admin delete foreign used leaf                                  | IT           | **Done** |
| Foreign user DELETE → `404`                                     | IT           | **Done** |
| PUT/PATCH parentCategory change → `400`                         | IT           | **Done** |
| PATCH omit/same parent → OK                                     | IT           | **Done** |
| `categoryType` change when in use → `400`                       | IT           | **Done** |
| `categoryType` change unused leaf → OK                          | IT           | **Done** |
| Create child with mismatched `categoryType` → `400`             | IT           | **Done** |
| Inactive blocks duplicate sibling name                          | IT           | **Done** |
| Rename used category (uniqueness OK)                            | IT           | **Done** |
| Delete cleanup + child guard                                    | Service unit | **Done** |
| Parent immutability + type guards                               | Service unit | **Done** |

---

# Grupo 2

## 6. FinancialSubscription

**Pattern:** A + optional links. **Relationships:** `account`, `category`, `tags` M2M; inverse `FinancialTransaction.financialSubscription`.
**Status:** **Done** (ownership + validations + domain rules).

**Concept:** recurring expected item (Netflix, rent, salary, etc.) — not the transaction itself.

### Baseline **Done** (ownership + validations)

| Rule                            | Implementation                                       |
| ------------------------------- | ---------------------------------------------------- |
| Pattern A ownership             | `findAccessibleEntity()`; admin bypass               |
| Optional links owned on actor   | POST/PUT/PATCH; foreign link → `400`                 |
| PATCH JsonNode link semantics   | `has("account")` / `has("category")` / `has("tags")` |
| REST `IllegalArgumentException` | `400` `error.invalid` `params=financialSubscription` |

### DELETE

| Rule                                         | Decision                                                          | Applies to admin | Error | Status   |
| -------------------------------------------- | ----------------------------------------------------------------- | ---------------- | ----- | -------- |
| Delete allowed when in use                   | **Yes** — unlink first, then delete row                           | Yes              | —     | **Done** |
| Delete only subscription row                 | Do not delete FT, account, category, tag entities                 | Yes              | —     | **Done** |
| `FinancialTransaction.financialSubscription` | Set `null`                                                        | Yes              | —     | **Done** |
| `rel_financial_subscription__tags`           | Remove join rows (explicit `@Modifying`)                          | Yes              | —     | **Done** |
| Guard order                                  | Access → cleanup FT → cleanup tag joins → `deleteById`            | Yes              | —     | **Done** |
| Single transaction                           | Cleanup + `deleteById` in `FinancialSubscriptionService.delete()` | Yes              | —     | **Done** |
| Foreign user DELETE                          | `404` (ownership first)                                           | —                | `404` | **Done** |
| Accessible / admin DELETE                    | `204` after cleanup                                               | Yes              | —     | **Done** |

**Implementation:** explicit `@Modifying` cleanup (`flushAutomatically` + `clearAutomatically`), then `deleteById`. Do **not** cascade-delete FT, account, category, or tags.

### UPDATE / PATCH

| Rule                                                                         | Decision                                                   | Applies to admin | Error         | Status   |
| ---------------------------------------------------------------------------- | ---------------------------------------------------------- | ---------------- | ------------- | -------- |
| Links validated vs **subscription owner**                                    | Not current admin actor — same as `TransactionRuleService` | Yes              | `400` invalid | **Done** |
| `account` optional, mutable                                                  | Null allowed; if set, owned by subscription user           | Yes              | `400` invalid | **Done** |
| `account.currency` == `subscription.currency`                                | When `account` present                                     | Yes              | `400` invalid | **Done** |
| `category` optional, mutable                                                 | If set, owned by subscription user                         | Yes              | `400` invalid | **Done** |
| `tags` mutable                                                               | All owned by subscription user                             | Yes              | `400` invalid | **Done** |
| `name`, `description`, `expectedAmount`, `amountTolerancePercentage` mutable |                                                            | Yes              | —             | **Done** |
| `recurrenceUnit`, `intervalCount` mutable                                    | Unless FT linked (structural)                              | Yes              | `400` invalid | **Done** |
| `startDate`, `endDate`, `nextExpectedDate` mutable                           | With date validation                                       | Yes              | `400` invalid | **Done** |
| `status` mutable (`ACTIVE` / `PAUSED` / `CANCELLED`)                         | Does **not** unlink FT or rules                            | Yes              | —             | **Done** |
| `automaticPayment`, `notes` mutable                                          |                                                            | Yes              | —             | **Done** |
| PATCH omit link fields                                                       | Preserves existing links                                   | Yes              | —             | **Done** |
| PATCH `null` / `[]`                                                          | Clears ManyToOne / M2M                                     | Yes              | —             | **Done** |

**Structural immutability when FT linked:** `currency`, `recurrenceUnit`, `intervalCount` cannot change while any `FinancialTransaction.financialSubscription` references this subscription. `TransactionRule` output alone does **not** block structural change.

**Date validation:** if `endDate` present → `endDate >= startDate`; if `nextExpectedDate` present → `nextExpectedDate >= startDate`.

### Product rules

| Rule                                     | Decision                                                       | Status                  |
| ---------------------------------------- | -------------------------------------------------------------- | ----------------------- |
| `status = PAUSED` or `CANCELLED`         | Soft-off; **links remain**; prefer over delete for history     | **Done**                |
| DELETE vs soft-off                       | DELETE unlinks FT and disables rules; soft-off preserves links | **Done**                |
| Import matching                          | Amount ± `amountTolerancePercentage`, date window              | **Open** (Fase 6)       |
| Auto-advance `nextExpectedDate` on match |                                                                | **Open**                |
| Does not auto-generate transactions      | JDL                                                            | **Done** (product fact) |
| Schedule generation / reminders          | Out of scope                                                   | **Deferred**            |

### Contrasts (intentional)

| Entity                    | DELETE when linked                              |
| ------------------------- | ----------------------------------------------- |
| **Tag**                   | Unlink M2M → delete tag                         |
| **Category**              | Block if children; leaf unlink+cleanup → delete |
| **FinancialSubscription** | Unlink FT + disable rules → delete (no block)   |

### Tests

| Test                                                                           | Layer        | Status   |
| ------------------------------------------------------------------------------ | ------------ | -------- |
| Delete unused subscription → `204`                                             | IT           | **Done** |
| Delete subscription linked to FT → FT survives, `financialSubscription = null` | IT           | **Done** |
| Delete subscription used by rule → rule survives, output null + `active=false` | IT           | **Done** |
| Delete used in FT + rule at once → both cleaned                                | IT           | **Done** |
| Delete does not delete account/category/tag entities                           | IT           | **Done** |
| Admin delete foreign used subscription → `204` + cleanup                       | IT           | **Done** |
| `status = CANCELLED` keeps FT linked                                           | IT           | **Done** |
| `endDate` / `nextExpectedDate` before `startDate` → `400`                      | IT           | **Done** |
| Change `currency` with FT linked → `400`; without FT → OK                      | IT           | **Done** |
| `account.currency` mismatch → `400`                                            | IT           | **Done** |
| Admin update foreign subscription with foreign owner's category → `400`        | IT           | **Done** |
| Cleanup before `deleteById`                                                    | Service unit | **Done** |

### UX — delete confirmation

| Rule                                                            | Status   |
| --------------------------------------------------------------- | -------- |
| Explain subscription row deleted; FT not deleted; links removed | **Done** |
| Action cannot be undone                                         | **Done** |

**Suggested copy:** “This will delete the subscription. Transactions that were linked to this subscription will not be deleted. They will simply no longer be associated with it. This action cannot be undone.”

---

## 7. Budget

**Pattern:** A + M2M. **Relationships:** `accounts`, `categories`, `tags` (all optional M2M).  
**Status:** **Done** (ownership + validations + domain rules).

### DELETE

| Rule                      | Decision                                           | Applies to admin | Error | Status   |
| ------------------------- | -------------------------------------------------- | ---------------- | ----- | -------- |
| Delete allowed            | **Yes** — explicit M2M cleanup, then `deleteById`  | Yes              | —     | **Done** |
| Delete only budget row    | Do not delete FA, Category, Tag, FT, Subscription  | Yes              | —     | **Done** |
| `rel_budget__accounts`    | Remove join rows (`@Modifying`)                    | Yes              | —     | **Done** |
| `rel_budget__categories`  | Remove join rows                                   | Yes              | —     | **Done** |
| `rel_budget__tags`        | Remove join rows                                   | Yes              | —     | **Done** |
| Single transaction        | Cleanup + `deleteById` in `BudgetService.delete()` | Yes              | —     | **Done** |
| Foreign user DELETE       | `404`                                              | —                | `404` | **Done** |
| Accessible / admin DELETE | `204` after cleanup                                | Yes              | —     | **Done** |

### Scope semantics (reporting — calculation out of scope)

| Rule               | Decision                                                | Status                |
| ------------------ | ------------------------------------------------------- | --------------------- |
| Empty `accounts`   | All **active** accounts of owner in **budget.currency** | **Done** (documented) |
| Empty `categories` | All applicable categories                               | **Done** (documented) |
| Empty `tags`       | No tag filter                                           | **Done** (documented) |
| All scopes empty   | Valid general budget for period/currency                | **Done** (documented) |

### UPDATE / PATCH

| Rule                                           | Decision                                              | Applies to admin | Error         | Status   |
| ---------------------------------------------- | ----------------------------------------------------- | ---------------- | ------------- | -------- |
| Links vs **budget owner** (`ownerLogin`)       | Not current admin actor                               | Yes              | `400` invalid | **Done** |
| M2M mutable                                    | add / remove / replace                                | Yes              | —             | **Done** |
| PATCH absent field                             | Preserves links                                       | Yes              | —             | **Done** |
| PATCH `null` / `[]`                            | Clears M2M                                            | Yes              | —             | **Done** |
| PATCH list                                     | Replaces with owner-valid resources                   | Yes              | —             | **Done** |
| `amount` > 0                                   | Overrides JDL `@DecimalMin("0")`                      | Yes              | `400` invalid | **Done** |
| `endDate` >= `startDate` when set              | Epoch day 0 = unset (JHipster sentinel)               | Yes              | `400` invalid | **Done** |
| Linked `account.currency` == `budget.currency` | Create / update / patch; currency change re-validates | Yes              | `400` invalid | **Done** |
| Linked categories                              | `EXPENSE` or `BOTH` only (v1 expense budget)          | Yes              | `400` invalid | **Done** |
| `status` PAUSED / COMPLETED                    | Preserves all M2M links                               | Yes              | —             | **Done** |
| `tagMatchMode`                                 | Required by model; no extra CRUD validation v1        | Yes              | —             | **Done** |

### Product rules (deferred)

| Rule                                    | Decision                                 | Status       |
| --------------------------------------- | ---------------------------------------- | ------------ |
| `tagMatchMode` when tags present        | ALL/ANY matching                         | **Open**     |
| Spend vs `amount` / `warningPercentage` | Sum matching OUT txs                     | **Open**     |
| Income budgets                          | Requires explicit Budget flow/type field | **Deferred** |

### Contrasts

| Entity       | DELETE when linked                      |
| ------------ | --------------------------------------- |
| **Budget**   | Unlink 3 M2M → delete (no block)        |
| **Category** | Block if children; leaf unlink → delete |

### UX — delete confirmation

Suggested copy documented; `budget-delete-dialog.tsx` + i18n en/es.

---

## 8. TransactionRuleCondition

**Pattern:** C — via `transactionRule`. **Relationships:** parent rule only (required, **immutable after create**).  
**Status:** **Done** (ownership + validations + delete side-effect).

> **Breaking change:** `transactionRule` reparent (including same-owner) is **no longer allowed**. Move condition = delete + create.

### DELETE

| Rule                              | Decision                                                             | Applies to admin | Error | Status   |
| --------------------------------- | -------------------------------------------------------------------- | ---------------- | ----- | -------- |
| Delete allowed                    | Delete only `TransactionRuleCondition` row                           | Yes              | —     | **Done** |
| Do not delete parent rule         | `TransactionRule` survives                                           | Yes              | —     | **Done** |
| Do not delete FT / financial data | —                                                                    | Yes              | —     | **Done** |
| Last condition on parent          | `TransactionRule.active = false`; update `updatedAt`                 | Yes              | —     | **Done** |
| Count before delete               | `count(conditions where rule_id = parentId)`; if `== 1` → deactivate | Yes              | —     | **Done** |
| Single transaction                | Delete + optional parent update                                      | Yes              | —     | **Done** |
| Parent already inactive           | Stays `false`                                                        | Yes              | —     | **Done** |
| Create on inactive rule           | Does **not** auto-reactivate parent                                  | Yes              | —     | **Done** |
| Foreign user DELETE               | `404`                                                                | —                | `404` | **Done** |
| Admin DELETE foreign              | `204`                                                                | Yes              | —     | **Done** |
| Parent rule DELETE cleanup        | `TransactionRuleService.delete` bulk deletes child conditions        | Yes              | —     | **Done** |

### CREATE — parent `transactionRule`

| Rule                           | Decision                                                     | Applies to admin | Error         | Status   |
| ------------------------------ | ------------------------------------------------------------ | ---------------- | ------------- | -------- |
| `transactionRule` required     | Resolve accessible parent                                    | Yes              | `400` invalid | **Done** |
| Normal user foreign parent     | —                                                            | No               | `400` invalid | **Done** |
| Admin foreign parent           | Allowed                                                      | Yes              | —             | **Done** |
| `ACCOUNT` values vs rule owner | Validate ids against `transactionRule.user.login`, not admin | Yes              | `400` invalid | **Done** |

### UPDATE / PATCH — parent `transactionRule` (immutable)

| Rule                                     | Decision                                     | Applies to admin | Error         | Status   |
| ---------------------------------------- | -------------------------------------------- | ---------------- | ------------- | -------- |
| PATCH omit `transactionRule`             | Preserve current parent                      | Yes              | —             | **Done** |
| PUT/PATCH same `transactionRule.id`      | Allowed (no-op)                              | Yes              | —             | **Done** |
| PUT/PATCH `transactionRule: null`        | —                                            | Yes              | `400` invalid | **Done** |
| PUT/PATCH different `transactionRule.id` | **Blocked** — even same owner                | Yes              | `400` invalid | **Done** |
| ~~Reparent same-owner~~                  | **Removed** — was **Done**, now **replaced** | —                | `400` invalid | **Done** |

### Mutable fields

Client-editable fields: `field`, `operator`, `value`, `secondValue`, `caseSensitive`.

`position` is server-managed. New conditions are appended using `max(existing position for rule) + 1` (`0` for the first condition). Client-provided `position` on create is ignored. PUT/PATCH preserve the existing value; explicit same value is allowed as a no-op, explicit changed value or explicit `null` is rejected with `400 invalid`.

Validate **final merged state** after PUT/PATCH (not only submitted fields). Example: PATCH operator `BETWEEN` → `EQUALS` while `secondValue` remains in DB → `400`.

### Field / operator compatibility

**TEXT** — `DESCRIPTION`, `EXTERNAL_REFERENCE`  
Operators: `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `NOT_CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`, `IN`, `NOT_IN`.  
`value` non-blank; `REGEX` must compile; `IN`/`NOT_IN` comma-separated; `secondValue` blank; `caseSensitive` applies.

**ENUM** — `FLOW`, `ORIGIN`  
Operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`.  
`value` = valid enum (`IN`/`OUT` for FLOW; `MANUAL`/`FILE_IMPORT`/`API` for ORIGIN); `secondValue` blank; `caseSensitive` persisted, ignored at execution.

**AMOUNT**  
Operators: `EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `BETWEEN`, `IN`, `NOT_IN`.  
Parse `BigDecimal` — dot decimal, no currency symbols, no thousands separators; `BETWEEN` requires `secondValue` and `value <= secondValue`; `caseSensitive` persisted, ignored.

**DATE** — `TRANSACTION_DATE`, `POSTING_DATE`  
Operators: `EQUALS`, `NOT_EQUALS`, `BEFORE`, `AFTER`, `BETWEEN`, `IN`, `NOT_IN`.  
ISO `yyyy-MM-dd`; `BETWEEN` rules as AMOUNT; `caseSensitive` persisted, ignored.

**ACCOUNT**  
Operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`.  
`value` / list = `Long` account ids; every id must exist and belong to **parent rule owner**; `secondValue` blank; `caseSensitive` persisted, ignored.

**Invalid combos** → `400 invalid` (e.g. `DESCRIPTION + GREATER_THAN`, `AMOUNT + CONTAINS`, `FLOW + REGEX`, `ORIGIN + CONTAINS`, `ACCOUNT + REGEX`, `POSTING_DATE + GREATER_THAN`).

### General value rules

| Rule                                 | Decision                                                            | Status   |
| ------------------------------------ | ------------------------------------------------------------------- | -------- |
| `value` required                     | Non-null, non-blank (trim)                                          | **Done** |
| `secondValue` for `BETWEEN` only     | Required; `value <= secondValue` after parse                        | **Done** |
| `secondValue` for non-`BETWEEN`      | Must be null/blank                                                  | **Done** |
| `position` ≥ 0                       | Server-managed append order; no unique position per rule            | **Done** |
| `caseSensitive`                      | Required; accept as provided; execution uses only on TEXT           | **Done** |
| PATCH required field explicit `null` | Reject `field`, `operator`, `value`, `caseSensitive`, or `position` | **Done** |
| PATCH `secondValue: null`            | Clear optional second value before merged-state validation          | **Done** |

### `IN` / `NOT_IN`

Comma-separated; trim tokens; empty token / blank value / `,,` / `A,,B` → `400`; one token valid; duplicate input tokens allowed but deduplicated for duplicate guard.

### Duplicate guard (service only)

Block exact duplicate inside same `TransactionRule`: same `field`, `operator`, normalized `value`, normalized `secondValue`, `caseSensitive` — **exclude** current id on update; **exclude** `position`. Allowed under different rules. No DB unique constraint.

**Normalization:** TEXT trim + lowercase if `caseSensitive=false`; REGEX trim preserve case; IN/NOT_IN split/trim/dedupe/sort/canonicalize per field type; AMOUNT → canonical BigDecimal string; DATE → ISO; FLOW/ORIGIN → enum name; ACCOUNT → Long string.

### UX — delete confirmation

Suggested copy: _"This will delete this rule condition. The rule itself will not be deleted. If this was the last condition, the rule will be disabled to prevent it from matching every transaction. This action cannot be undone."_

### UX — smart condition form

The standalone TransactionRuleCondition create/edit form and embedded TransactionRule detail condition editor are no longer raw generated enum forms. They guide the user toward combinations accepted by the backend:

| Field type                          | UI behavior                                                                                                                                                                                     | Status   |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| `DESCRIPTION`, `EXTERNAL_REFERENCE` | Shows only text operators; value is text; `caseSensitive` is visible                                                                                                                            | **Done** |
| `AMOUNT`                            | Shows numeric/list operators; value is number except `IN`/`NOT_IN` text list; `BETWEEN` second value is number                                                                                  | **Done** |
| `TRANSACTION_DATE`, `POSTING_DATE`  | Shows date/list operators; value is date except `IN`/`NOT_IN` text list; `BETWEEN` second value is date                                                                                         | **Done** |
| `FLOW`                              | Shows enum operators; `EQUALS`/`NOT_EQUALS` use an `IN`/`OUT` select; `IN`/`NOT_IN` remain comma-separated text                                                                                 | **Done** |
| `ORIGIN`                            | Shows enum operators; `EQUALS`/`NOT_EQUALS` use a `MANUAL`/`FILE_IMPORT`/`API` select; `IN`/`NOT_IN` remain comma-separated text                                                                | **Done** |
| `ACCOUNT`                           | Shows account operators; `EQUALS`/`NOT_EQUALS` use an accessible FinancialAccount selector and submit the selected account id as a string; `IN`/`NOT_IN` remain comma-separated account id text | **Done** |
| `secondValue`                       | Visible only for `BETWEEN`; hidden operators submit/clear it as `null`                                                                                                                          | **Done** |
| `caseSensitive`                     | Visible only for text fields; hidden fields submit `false`                                                                                                                                      | **Done** |
| Field/operator changes              | Incompatible operator/value/secondValue are cleared or reset client-side                                                                                                                        | **Done** |
| Embedded table display              | TransactionRule detail shows a normalized condition summary; raw `value`, `secondValue`, and `caseSensitive` are not separate embedded table columns                                            | **Done** |

### Out of scope

Rule execution engine; batch reclassification; unique `position`; new enum values; condition reorder UI/API.

### Product rules (deferred)

| Rule                 | Decision                           | Status       |
| -------------------- | ---------------------------------- | ------------ |
| Condition evaluation | Part of TransactionRule motor (#9) | **Deferred** |

---

## 9. TransactionRule

**Pattern:** A + optional outputs. **Relationships:** conditions (1..\*), M2M/FK outputs.

### Normalization

| Rule                              | Decision                                         | Status   |
| --------------------------------- | ------------------------------------------------ | -------- |
| `name` trim before persist        | Trimmed value is stored                          | **Done** |
| `name` non-blank after trim       | Blank → `400 invalid`                            | **Done** |
| `name` max length                 | Applies after trim                               | **Done** |
| `name` uniqueness                 | Per owner, case-insensitive and trim-insensitive | **Done** |
| Inactive rule names               | Still reserve the name                           | **Done** |
| `description` trim before persist | Blank becomes `null`; max length after trim      | **Done** |

### Timestamps

| Rule                                  | Decision                                                              | Applies to admin | Error         | Status   |
| ------------------------------------- | --------------------------------------------------------------------- | ---------------- | ------------- | -------- |
| Server-owned timestamps               | Client-provided `createdAt` / `updatedAt` cannot change stored values | Yes              | —             | **Done** |
| CREATE timestamps                     | Ignore client values; set both to `Instant.now()`                     | Yes              | —             | **Done** |
| UPDATE / PATCH `createdAt`            | Preserve existing value                                               | Yes              | —             | **Done** |
| Client sends null/changed `createdAt` | Reject when explicit null or different from existing                  | Yes              | `400 invalid` | **Done** |
| Client sends same `createdAt`         | Allow as no-op                                                        | Yes              | —             | **Done** |
| Client sends null/changed `updatedAt` | Reject when explicit null or different from existing                  | Yes              | `400 invalid` | **Done** |
| Client sends same `updatedAt`         | Allow as no-op before server sets `now`                               | Yes              | —             | **Done** |
| Successful UPDATE / PATCH             | Set `updatedAt = Instant.now()`                                       | Yes              | —             | **Done** |

### DELETE

| Rule                                                   | Decision                                                                                | Applies to admin | Error                 | Status   |
| ------------------------------------------------------ | --------------------------------------------------------------------------------------- | ---------------- | --------------------- | -------- |
| Delete allowed                                         | Delete `TransactionRule` row                                                            | Yes              | `404` if inaccessible | **Done** |
| Delete child conditions                                | Explicit `TransactionRuleConditionRepository.deleteByTransactionRuleId(ruleId)`         | Yes              | —                     | **Done** |
| Clear resulting tags join rows                         | Explicit `TransactionRuleRepository` cleanup for `rel_transaction_rule__resulting_tags` | Yes              | —                     | **Done** |
| Do not call `TransactionRuleConditionService.delete()` | Avoid last-condition side-effect because parent is being deleted                        | Yes              | —                     | **Done** |
| Do not delete output entities                          | Category, FinancialSubscription, Tag survive                                            | Yes              | —                     | **Done** |
| Do not delete transactions                             | FinancialTransactions survive                                                           | Yes              | —                     | **Done** |
| Single transaction                                     | Cleanup + delete                                                                        | Yes              | —                     | **Done** |

**Delete order:** resolve accessible rule → delete conditions by rule id → clear resultingTags join table → delete `TransactionRule`.

### UPDATE / PATCH

| Rule                         | Decision                                                                                      | Status   |
| ---------------------------- | --------------------------------------------------------------------------------------------- | -------- |
| Outputs ⊆ rule owner         |                                                                                               | **Done** |
| `user` immutable             | Client payload cannot change owner                                                            | **Done** |
| Mutable fields               | `name`, `description`, `conditionLogic`, `active`, outputs                                    | **Done** |
| `priority` server-managed    | Omitted on PUT/PATCH preserves; same value is a no-op; explicit null/changed value → `400`    | **Done** |
| PUT contract                 | Full DTO update, except server-managed `priority` may be omitted and preserved                | **Done** |
| Conditions inline            | TransactionRule create/update/patch never creates, updates, or deletes conditions inline      | **Done** |
| Conditions owner             | Conditions are managed only through `TransactionRuleCondition`                                | **Done** |
| PATCH required scalar `null` | `name`, `priority`, `conditionLogic`, `active`, `createdAt`, `updatedAt` null → `400 invalid` | **Done** |
| PATCH output link semantics  | JsonNode presence-aware                                                                       | **Done** |

### Priority / evaluation order

| Rule                           | Decision                                                                                | Status   |
| ------------------------------ | --------------------------------------------------------------------------------------- | -------- |
| Server-managed                 | Users cannot edit `priority` as a free numeric field                                    | **Done** |
| Scope                          | Unique/consecutive per owner/user, not global                                           | **Done** |
| Internal numbering             | Stored 0-based: `0, 1, 2, ...`                                                          | **Done** |
| Create                         | Ignore client-supplied priority; append with `max(priority for user) + 1`, or `0` first | **Done** |
| Delete                         | Reindex remaining rules for the same owner/user only                                    | **Done** |
| Delete ordering                | Reindex preserves existing order by `priority ASC, id ASC`                              | **Done** |
| UI display                     | Shows 1-based order (`#1`, `#2`, ...) and does not submit priority from create/edit     | **Done** |
| Reorder UI/API                 | Move up / Move down sends a full owner-scoped ordered id list                           | **Done** |
| Rule engine evaluation         | Phase 1 pure evaluator reads active rules by `priority ASC, id ASC`; no mutation        | **Done** |
| Admin-specific cross-user eval | Not supported; evaluation uses the transaction/account owner under normal-user rules    | **Done** |

### Output requirements

| Rule                       | Decision                                                                                    | Applies to admin | Error         | Status   |
| -------------------------- | ------------------------------------------------------------------------------------------- | ---------------- | ------------- | -------- |
| At least one output        | Final merged state must have category or non-empty tags                                     | Yes              | `400 invalid` | **Done** |
| No output                  | Reject create/update/patch                                                                  | Yes              | `400 invalid` | **Done** |
| Output links vs rule owner | Validate against rule owner, not current admin actor                                        | Yes              | `400 invalid` | **Done** |
| Inactive linked outputs    | Allowed at backend level                                                                    | Yes              | —             | **Done** |
| Deferred outputs           | Subscription and description mutations are not outputs in the current TransactionRule model | Yes              | —             | **Done** |

### Active / conditions

| Rule                                 | Decision                                        | Applies to admin | Error         | Status          |
| ------------------------------------ | ----------------------------------------------- | ---------------- | ------------- | --------------- |
| `active=true` requires conditions    | At least one existing condition required        | Yes              | `400 invalid` | **Done**        |
| `active=false` with zero conditions  | Allowed when rule has at least one valid output | Yes              | —             | **Done**        |
| Reactivate with zero conditions      | Reject                                          | Yes              | `400 invalid` | **Done**        |
| Create condition under inactive rule | Does not auto-reactivate                        | Yes              | —             | **Done** in TRC |

**UI/API flow:** create inactive draft → add conditions through `TransactionRuleCondition` → activate rule.

### Parent-centered UX / API

| Rule                                    | Decision                                                                                                           | Status       |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ------------ |
| Related conditions endpoint             | `GET /api/transaction-rules/{id}/conditions` returns conditions for an accessible rule                             | **Done**     |
| Related condition ordering              | Sort by `position ASC`, then `id ASC`; distinct from TransactionRule priority/order                                | **Done**     |
| Related condition access                | Normal users get own rules only; foreign/inaccessible parent → `404`; admin may read all                           | **Done**     |
| Create flow                             | Create saves an inactive parent first, then redirects to detail so conditions can be added after the parent exists | **Done**     |
| Create page children                    | No embedded condition editor and no client-side draft conditions on create                                         | **Done**     |
| Rule detail condition collection editor | Inline create/update/delete using existing TransactionRuleCondition endpoints                                      | **Done**     |
| Rule edit general-fields page           | No embedded condition editor; includes Manage conditions link and background condition count for Active safety     | **Done**     |
| Rule list/detail semantic summaries     | List/detail avoid raw generated field dumps and show read-only order, status, condition logic, outputs, metadata   | **Done**     |
| View/edit layout parity                 | Detail and edit share Identity → Matching logic → Result → Status/Metadata ordering; detail manages conditions     | **Done**     |
| Embedded parent behavior                | Does not show/edit `transactionRule`; create submits current parent id; edit does not reparent                     | **Done**     |
| Active toggle UX                        | Disabled when conditions are empty/unavailable; backend remains source of truth                                    | **Done**     |
| Add condition side effect               | Adding a condition does not auto-activate the parent rule                                                          | **Done**     |
| Create-with-conditions command          | Atomic parent+conditions command endpoint                                                                          | **Deferred** |
| Draft child collection on create        | Client-side draft conditions before parent id exists                                                               | **Deferred** |
| Row-positioned inline edit              | Current editor renders add/edit form above the table, not directly under the row                                   | **Deferred** |
| Server-managed condition position       | Create appends with `max(position)+1`; delete does not reindex; same-position ties sort by `id ASC`                | **Done**     |
| Condition reorder UI/API                | No drag/drop, manual position input, or reorder endpoint yet                                                       | **Deferred** |
| Rule execution engine                   | Backend evaluator exists; embedded UI still does not preview/apply rules                                           | **Done**     |

### Output PATCH semantics

| Field type                            | Semantics                                   | Status   |
| ------------------------------------- | ------------------------------------------- | -------- |
| ManyToOne absent                      | Preserve current link                       | **Done** |
| ManyToOne `null`                      | Clear link                                  | **Done** |
| ManyToOne object with valid id        | Replace after owner validation              | **Done** |
| ManyToOne object with null/missing id | `400 invalid`                               | **Done** |
| `resultingTags` absent                | Preserve tags                               | **Done** |
| `resultingTags: null` / `[]`          | Clear all tags                              | **Done** |
| `resultingTags` list                  | Replace full set after owner validation     | **Done** |
| Tag object with null/missing id       | `400 invalid`                               | **Done** |
| Foreign tag id                        | `400 invalid`                               | **Done** |
| Admin editing foreign rule            | Validate tags against rule owner, not admin | **Done** |

Applies to the current ManyToOne output: `resultingCategory`.

### Active false

| Rule                   | Decision                                                     | Status   |
| ---------------------- | ------------------------------------------------------------ | -------- |
| Deactivation           | Does not delete anything                                     | **Done** |
| Output preservation    | Does not clear category/tags                                 | **Done** |
| Condition preservation | Does not clear child conditions                              | **Done** |
| Execution              | Prevents evaluator matching and future automatic application | **Done** |

### Interactions with other deletes

| Source delete          | TransactionRule effect                                     | Owner service            |
| ---------------------- | ---------------------------------------------------------- | ------------------------ |
| Category leaf delete   | Clear `resultingCategory`; set `active=false`              | `CategoryService`        |
| Tag delete             | Remove tag from `resultingTags`                            | `TagService`             |
| TransactionRule delete | Owns deleting only rule + rule conditions + rule tag joins | `TransactionRuleService` |

### UX — delete confirmation

Suggested copy: _"This will delete the rule. Its conditions will also be deleted. Categories, subscriptions, tags, and transactions will not be deleted. Future transactions will no longer be classified by this rule. This action cannot be undone."_

### Product rules

| Rule                                          | Decision                                                                                                               | Status       |
| --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------ |
| Evaluate on FT **create** only                | Apply matching rules on create with `FILL_EMPTY_ONLY`; no update/PATCH application; no `MANUAL`-only restriction today | **Done**     |
| Lower `priority` evaluates earlier            | Phase 1 evaluator uses `priority ASC, id ASC`; see [RULE-ENGINE.md](RULE-ENGINE.md)                                    | **Done**     |
| Tags union from all matching rules            | Phase 1 evaluator accumulates tag suggestions; see [RULE-ENGINE.md](RULE-ENGINE.md)                                    | **Done**     |
| Duplicate priorities                          | Not allowed by service-managed per-user consecutive ordering                                                           | **Done**     |
| Manual rule reorder                           | Move up / Move down sends full ordered ids; backend validates exact owner set                                          | **Done**     |
| Manual/source FT fields override rule outputs | Explicit category/tags win by default; evaluator/preview returns suggestions/conflicts without mutating                | **Done**     |
| Rule preview endpoint                         | `POST /api/financial-transactions/rule-preview` previews an unsaved draft; no save/mutation/application                | **Done**     |
| Rule execution engine                         | Phase 3B two-step manual create preview UI implemented; reevaluation/bulk remain deferred                              | **Done**     |
| Rule evaluation ownership                     | Evaluate only the transaction/account owner's rules; admin has no special rule-evaluation override                     | **Done**     |
| Batch reclassification                        | Not part of CRUD domain-rule pass                                                                                      | **Deferred** |

### Rule Engine design

The Transaction Rule Engine is documented in [RULE-ENGINE.md](RULE-ENGINE.md).

Implemented today: rule authoring, validation, ordering, active/condition guards, condition management, a backend-only pure evaluator, `FILL_EMPTY_ONLY` application on `FinancialTransaction` create, backend-only draft preview via `POST /api/financial-transactions/rule-preview`, and the manual FinancialTransaction create two-step preview UI.

Not implemented today: rule application on update/PATCH, existing-transaction reevaluation, bulk reclassification, persisted evaluation result, override confirmation UI, and audit/explanation UI.

Origin policy remains open for future API/import/ingestion runtime. Current behavior is intentionally not restricted to `MANUAL` origin only; future runtime work must decide whether non-manual flows should use central create with rule application, bypass it, make it configurable, preview only, or apply only in specific modes.

---

## 10. ApiAccessToken

**Pattern:** A. **Relationships:** `ApiAccessTokenPermission` children only. **No FK** from `ApiIngestion` — historical ingestions use **snapshot audit fields** on `ApiIngestion` (decision **11C**).

### DELETE

| Rule                                                       | Decision                                                              | Applies to admin | Error                   | Status         |
| ---------------------------------------------------------- | --------------------------------------------------------------------- | ---------------- | ----------------------- | -------------- |
| Allowed even if historical `ApiIngestion`s used this token | Delete token row; ingestions + transactions **unaffected**            | Yes              | —                       | **Done** (11C) |
| No `ApiIngestion` cleanup on delete                        | Snapshots retain `apiTokenIdSnapshot` / prefix / name                 | Yes              | —                       | **Done** (11C) |
| Cascade delete permissions                                 | Delete `ApiAccessTokenPermission` children first                      | Yes              | —                       | **Done**       |
| Delete order                                               | 1) resolve accessible token → 2) delete permissions → 3) delete token | Yes              | `404` if not accessible | **Done** (11C) |
| Normal user                                                | Only own tokens                                                       | No               | `404`                   | **Done**       |
| Admin                                                      | May delete foreign tokens                                             | Yes              | —                       | **Done**       |

### UPDATE / PATCH

| Rule                                                             | Decision                                                                                                                                    | Status         |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| HTTP request contract is operation-specific                      | POST uses strict `ApiAccessTokenCreateRequestDTO`; PUT uses strict `ApiAccessTokenUpdateRequestDTO`; response/read uses `ApiAccessTokenDTO` | **Done**       |
| `tokenHash` / `tokenPrefix` are server-generated                 | Client-provided values are invalid on create                                                                                                | **Done**       |
| `tokenHash` / `tokenPrefix` immutable                            | PATCH explicit null is invalid, not a silent no-op                                                                                          | **Done**       |
| `tokenHash` omitted on GET                                       |                                                                                                                                             | **Done**       |
| Owner immutable                                                  |                                                                                                                                             | **Done**       |
| `createdAt`, `updatedAt`, `lastUsedAt`, `revokedAt` server-owned | `createdAt`/`lastUsedAt`/`revokedAt` preserve existing values; `updatedAt` set by server on successful update/patch                         | **Done**       |
| `expiresAt` immutable after create                               |                                                                                                                                             | **Proposed**   |
| `status` `ACTIVE` → `REVOKE` allowed                             |                                                                                                                                             | **Proposed**   |
| `status` `REVOKE` → `ACTIVE` forbidden                           |                                                                                                                                             | **Proposed**   |
| `name` mutable                                                   | Does **not** retroactively change `ApiIngestion` snapshots                                                                                  | **Done** (11C) |

### Product rules

| Rule                                      | Decision                                                                                                                                | Status                |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------- |
| User-owned; token generated server-side   | POST request accepts name only for credential generation; raw token shown **once** on create (`rawToken` in POST response + copy modal) | **Done**              |
| `tokenHash` stored only; never exposed    | `tokenPrefix` safe to expose                                                                                                            | **Done**              |
| `status = REVOKED` blocks **new** API use | Historical ingestions remain via snapshots                                                                                              | **Proposed** (fase 6) |
| Deleted token blocks **new** API use      | Row gone; snapshots still identify past use                                                                                             | **Done** (11C)        |
| `expiresAt` enforcement                   |                                                                                                                                         | **Proposed** (fase 6) |
| `lastUsedAt` update on API call           |                                                                                                                                         | **Open**              |
| **REVOKE** vs **DELETE**                  | REVOKE disables token but keeps row visible; DELETE removes row; ingestion history survives either way                                  | **Done** (11C)        |
| UI delete copy                            | Must explain deleting a token does **not** delete ingestion history                                                                     | **Done** (11C)        |

**Migration (11C) ✅:** removed `ApiAccessToken.apiIngestions` one-to-many; removed ingestion-count guard on delete; Liquibase `20260711160000` adds snapshot columns and drops `api_access_token_id` FK.

---

# Grupo 3

## 11. InternalTransfer

**Pattern:** D — via both transaction legs.

### DELETE

| Rule          | Decision                                           | Applies to admin | Error | Status   |
| ------------- | -------------------------------------------------- | ---------------- | ----- | -------- |
| Simple delete | Transfer row only; **both transactions preserved** | Yes              | `404` | **Done** |

### UPDATE / PATCH

| Rule                                            | Decision                                                                     | Status   |
| ----------------------------------------------- | ---------------------------------------------------------------------------- | -------- |
| Distinct accounts, same currency/amount, OUT+IN |                                                                              | **Done** |
| Origin unrestricted                             | `MANUAL`, `FILE_IMPORT`, and `API` legs may be linked in any combination     | **Done** |
| Same owner both legs                            |                                                                              | **Done** |
| No duplicate participation                      | A transaction cannot participate in more than one transfer in either role    | **Done** |
| Legs + `createdAt` immutable                    |                                                                              | **Done** |
| Server-owned `createdAt`                        | Create ignores client value; PUT/PATCH preserve; changed/null patch rejected | **Done** |
| `notes` normalization                           | Trim before persist; blank/null clears; max 500 after trim                   | **Done** |
| Only `notes` mutable                            |                                                                              | **Done** |

### Product rules

| Rule                             | Decision           | Status   |
| -------------------------------- | ------------------ | -------- |
| Atomic create (out+in+transfer)  |                    | **Open** |
| Balance effects                  |                    | **Open** |
| Posting date alignment           |                    | **Open** |
| Date window/alignment validation | Not required in v1 | **Done** |

---

## 12. FinancialTransaction

**Pattern:** B — via `account`. **Relationships:** account (required), category, subscription, tags, ingestion, internal transfer legs, ingestion record.

### DELETE

| Rule                            | Decision                                                                                                                                                                                                                                                                                                                     | Applies to admin | Error | Status       |
| ------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- | ----- | ------------ |
| Standalone delete               | If linked `IngestionRecord` exists: set `status=REJECTED`, `errorCode=FINANCIAL_TRANSACTION_DELETED`, `errorMessage="Financial transaction was deleted manually."`, unlink `financialTransaction`; then delete any `InternalTransfer` link involving this transaction, clear tag join rows, and delete only this transaction | Yes              | —     | **Done**     |
| `deleteAllForAccount(account)`  | Per-account cleanup for FA orchestration                                                                                                                                                                                                                                                                                     | Yes              | —     | **Proposed** |
| Transfer leg on deleted account | Delete transfer + this leg; preserve other leg                                                                                                                                                                                                                                                                               | Yes              | —     | **Proposed** |

### UPDATE / PATCH

| Rule                                                                                                  | Decision                                                                                                                                             | Status   |
| ----------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| Create accepts valid `origin`; if `transactionIngestion` is present, origin must match ingestion type | FILE → `FILE_IMPORT`; API → `API`                                                                                                                    | **Done** |
| `account` required and immutable                                                                      | Create requires accessible account; PUT/PATCH same id only; null/different id invalid                                                                | **Done** |
| Server-owned timestamps                                                                               | Create ignores client timestamps; PUT/PATCH preserves `createdAt`, rejects explicit timestamp null/change, sets `updatedAt=now` on successful update | **Done** |
| Text normalization                                                                                    | Trim `description`/`externalReference`/`notes`; blank optional text becomes null; description blank invalid                                          | **Done** |
| `amount > 0`, scale 2                                                                                 | Amount must be positive and have at most 2 decimals                                                                                                  | **Done** |
| InternalTransfer guard                                                                                | If linked as either transfer leg, changing amount/flow is invalid; delete still allowed and removes only the transfer link                           | **Done** |
| Owner-scoped links                                                                                    | Category, tags, subscription validated against transaction account owner; admin editing foreign tx validates links against tx owner, not admin       | **Done** |
| Category compatibility                                                                                | OUT → EXPENSE/BOTH; IN → INCOME/BOTH; final merged state validated                                                                                   | **Done** |
| Subscription compatibility                                                                            | Same owner, currency matches account, and subscription account (if set) matches tx account                                                           | **Done** |
| PATCH relationship semantics                                                                          | Absent preserves; null clears optional links/sets; object/list ids replace after owner validation; missing/null ids invalid                          | **Done** |
| `transactionIngestion` immutable once set                                                             | Create may set valid ingestion; update/patch absent/same preserves, null/different invalid once set                                                  | **Done** |

### Product rules

| Rule                                | Decision                                       | Status           |
| ----------------------------------- | ---------------------------------------------- | ---------------- |
| Rule motor applies on create        | See #9                                         | **Proposed**     |
| Effective category (manual vs rule) |                                                | **Open**         |
| Balances                            | Do not update persisted balances in this phase | **Out of scope** |

---

## 13. TransactionIngestion

**Pattern:** B — via `account`. **Children:** `FileIngestion` OR `ApiIngestion`, `IngestionRecord`s, FTs.

### DELETE

| Rule                           | Decision                               | Applies to admin | Error                   | Status       |
| ------------------------------ | -------------------------------------- | ---------------- | ----------------------- | ------------ |
| Standalone delete              | Owned delete with explicit child order | Yes              | `404` when inaccessible | **Done**     |
| `deleteAllForAccount(account)` | Called by FA orchestration only        | Yes              | —                       | **Proposed** |

**Child order (owned by this service — Done):**

| Step | Action                                                                                                              |
| ---- | ------------------------------------------------------------------------------------------------------------------- |
| 1    | Delete `FileIngestion` by `transactionIngestion.id`                                                                 |
| 2    | Delete `ApiIngestion` by `transactionIngestion.id`                                                                  |
| 3    | Delete `InternalTransfer` links involving transactions from this ingestion, preserving the opposite transaction row |
| 4    | Delete `IngestionRecord`s linked to this ingestion before deleting any referenced `FinancialTransaction`            |
| 5    | Clear `rel_financial_transaction__tags` rows for transactions from this ingestion                                   |
| 6    | Delete `FinancialTransaction`s linked to this ingestion                                                             |
| 7    | Delete `TransactionIngestion`                                                                                       |

### UPDATE / PATCH

| Rule                                   | Decision                                                                                           | Status              |
| -------------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------- |
| `account` / `ingestionType` immutable  |                                                                                                    | **Done**            |
| Server timestamps / counters on create |                                                                                                    | **Done**            |
| Status lifecycle                       | `PENDING -> PROCESSING/final`; `PROCESSING -> final`; final terminal                               | **Done**            |
| Counts by status                       | in-progress `processed <= received`; final `processed == received`; status-specific rules          | **Done**            |
| `completedAt`                          | server-owned; final sets now; non-final requires null                                              | **Done**            |
| Child metadata consistency             | FILE cannot have API metadata; API cannot have file metadata; final status requires matching child | **Done**            |
| `status` / counters mutable            |                                                                                                    | **Done** (baseline) |

### Product rules

| Rule                                                   | Decision | Status       |
| ------------------------------------------------------ | -------- | ------------ |
| Status machine PENDING → PROCESSING → COMPLETED/FAILED |          | **Proposed** |
| Counter consistency with records                       |          | **Proposed** |
| Block delete while PROCESSING?                         |          | **Open**     |

---

## 14. FileIngestion

**Pattern:** C — via `transactionIngestion` (FILE, 1:1).

### DELETE

| Rule                                           | Decision                                                                                         | Applies to admin | Error         | Status   |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------ | ---------------- | ------------- | -------- |
| Never deleted directly by FileIngestionService | Lifecycle delegated to `TransactionIngestionService`                                             | Yes              | `400` invalid | **Done** |
| Parent delete cleanup                          | `TransactionIngestionService.delete()` deletes FileIngestion by parent id before deleting parent | Yes              | —             | **Done** |

### UPDATE / PATCH

| Rule                            | Decision                                                                                          | Status              |
| ------------------------------- | ------------------------------------------------------------------------------------------------- | ------------------- |
| Parent immutable                |                                                                                                   | **Done**            |
| FILE type + 1:1 guard on create |                                                                                                   | **Done**            |
| Server-owned `createdAt`        | Create ignores client value; PUT/PATCH preserve; changed/null rejected                            | **Done**            |
| Immutable metadata              | original filename, file type, content type, file size, checksum, storage key, parser name/version | **Done**            |
| Mutable statement dates         | nullable; final state requires start <= end                                                       | **Done**            |
| String normalization            | trim; optional blanks → null; hex checksum lowercased                                             | **Done**            |
| `storageKey` exposure risk      | DTO still exposes `storageKey`; must not contain public URLs or secrets                           | **Documented risk** |

### Product rules

| Rule                  | Decision | Status                |
| --------------------- | -------- | --------------------- |
| File upload + storage |          | **Proposed** (Fase 6) |
| Parser execution      |          | **Proposed** (Fase 6) |

---

## 15. ApiIngestion

**Pattern:** C — via `transactionIngestion.account.user`. **Token audit:** snapshot fields only — **no required FK** to `ApiAccessToken` (decision **11C**).

### Model — token snapshot fields (11C)

| Field                       | Type             | Notes                         |
| --------------------------- | ---------------- | ----------------------------- |
| `apiTokenIdSnapshot`        | `Long`           | Token id at ingestion time    |
| `apiTokenPrefixSnapshot`    | `String` max 20  | Safe prefix at ingestion time |
| `apiTokenNameSnapshot`      | `String` max 100 | Token name at ingestion time  |
| `apiTokenUserLoginSnapshot` | `String`         | Optional future — not v1      |
| `apiTokenStatusSnapshot`    | enum             | Optional future — not v1      |

**Removed:** `ApiIngestion.apiAccessToken` `@ManyToOne(optional = false)`. `ApiIngestion` must **not** depend on `ApiAccessToken` row existing.

### CREATE — snapshot semantics (11C)

| Rule                                             | Decision                                                                                                                           | Status         |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| When request authenticated with `ApiAccessToken` | Copy `id`, `tokenPrefix`, `name` into snapshot fields                                                                              | **Done** (11C) |
| Snapshots are **historical**                     | Never updated if token renamed, revoked, expired, or deleted                                                                       | **Done** (11C) |
| REST/admin create (pre-runtime)                  | Resolve accessible token from `ApiIngestionCreateRequestDTO.apiAccessTokenId`; same-owner guard; copy snapshots; **no FK persist** | **Done** (11C) |
| Parent `transactionIngestion`                    | Scoped + `ingestionType = API` + 1:1 guard                                                                                         | **Done**       |
| `requestId` unique global                        |                                                                                                                                    | **Done**       |
| Server `createdAt` / `receivedAt`                |                                                                                                                                    | **Done**       |

### DELETE

| Rule                                            | Decision                                                                                        | Applies to admin | Error         | Status         |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------- | ---------------- | ------------- | -------------- |
| Never deleted directly by `ApiIngestionService` | Lifecycle delegated to `TransactionIngestionService`                                            | Yes              | `400` invalid | **Done**       |
| Parent delete cleanup                           | `TransactionIngestionService.delete()` deletes ApiIngestion by parent id before deleting parent | Yes              | —             | **Done**       |
| Token delete does **not** delete `ApiIngestion` | Snapshots preserve audit trail                                                                  | Yes              | —             | **Done** (11C) |

### UPDATE / PATCH

| Rule                                           | Decision                                                                                                                              | Status         |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------- |
| `transactionIngestion` + `requestId` immutable | absent preserves; same id/value no-op; null/different `400`                                                                           | **Done**       |
| Token snapshot fields immutable after create   |                                                                                                                                       | **Done** (11C) |
| API metadata immutable after create            | `idempotencyKey`, `sourceSystem`, `apiVersion`, `endpoint`, `clientReference`, `receivedAt`, `createdAt` have no mutable v1 semantics | **Done**       |
| List/read shows snapshot fields                | UI displays prefix/name even if token deleted                                                                                         | **Done** (11C) |

### Product rules

| Rule                                                        | Decision                                    | Status                |
| ----------------------------------------------------------- | ------------------------------------------- | --------------------- |
| API handler + idempotency                                   | Runtime path sets snapshots from auth token | **Proposed** (Fase 6) |
| Revoked/deleted token cannot authenticate **new** ingestion | Existing rows unaffected                    | **Proposed** (fase 6) |

**Migration (11C) ✅:** JDL + `.jhipster/ApiIngestion.json`; Liquibase `20260711160000`; DTOs/mappers/repos/services/UI/tests updated; `apiAccessToken` removed from persistence and response DTOs — `ApiIngestionCreateRequestDTO.apiAccessTokenId` on POST is used only for snapshot capture.

---

## 16. IngestionRecord

**Pattern:** C — via `transactionIngestion`; optional 1:1 `financialTransaction`.

### DELETE

| Rule                  | Decision                                                                          | Applies to admin | Error         | Status   |
| --------------------- | --------------------------------------------------------------------------------- | ---------------- | ------------- | -------- |
| Direct DELETE blocked | Records are deleted only through `TransactionIngestionService` cleanup/revert     | Yes              | `400` invalid | **Done** |
| Foreign direct DELETE | Normal users cannot see foreign records                                           | No               | `404`         | **Done** |
| Parent cleanup        | `TransactionIngestionService.delete()` deletes records before deleting linked FTs | Yes              | —             | **Done** |

### UPDATE / PATCH

| Rule                                                                            | Decision                                                                                                                                                          | Status   |
| ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| Parent / `recordIndex` / `externalRecordId` / `rawData` / `createdAt` immutable | `null`, missing id, or changed value → `400`; absent preserves                                                                                                    | **Done** |
| `recordIndex` unique per ingestion                                              |                                                                                                                                                                   | **Done** |
| `externalRecordId` unique per ingestion when non-null                           | trim + blank→`null`; same value allowed in different parent                                                                                                       | **Done** |
| FT 1:1 guard                                                                    | Optional generally; required for `IMPORTED`; forbidden for `VALID`/`DISABLED`/`SKIPPED_DUPLICATE`/`REJECTED`/`FAILED`; must belong to same `TransactionIngestion` | **Done** |
| Parent final freeze                                                             | `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED` allow only no-op updates                                                                                             | **Done** |
| Mutable outcome fields                                                          | While parent `PENDING`/`PROCESSING`: `status`, `financialTransaction` if not already set, `errorCode`, `errorMessage`                                             | **Done** |
| Status consistency                                                              | `VALID` forbids FT/errors; `IMPORTED` requires FT and no errors; `DISABLED`/`SKIPPED_DUPLICATE` forbid FT; `REJECTED`/`FAILED` forbid FT and require errorMessage | **Done** |
| Safe logs                                                                       | `toString()` does not print rawData contents                                                                                                                      | **Done** |

### Product rules

| Rule                                     | Decision | Status                |
| ---------------------------------------- | -------- | --------------------- |
| Record → FT creation on pipeline success |          | **Proposed** (Fase 6) |

**Out of scope:** count reconciliation with parent `TransactionIngestion`; pipeline execution; full FT domain rules.

---

## CSV Ingestion v1 workflow

**Scope:** canonical CSV import workflow using the existing ingestion entities. This is distinct from generated CRUD pages for `TransactionIngestion`, `FileIngestion`, and `IngestionRecord`.

### I1 — persisted preview

| Rule                     | Decision                                                                                                                                                                                                           | Status       |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------ |
| Parent batch             | `TransactionIngestion` is the parent import batch/process and owns the target `FinancialAccount`                                                                                                                   | **Done**     |
| File metadata            | `FileIngestion` is metadata only and links 1:1 to `TransactionIngestion`; it does not have its own account field                                                                                                   | **Done**     |
| Account derivation       | `FileIngestion` derives account through `TransactionIngestion.account`                                                                                                                                             | **Done**     |
| Preview records          | `IngestionRecord` represents one CSV data row                                                                                                                                                                      | **Done**     |
| No transactions in I1    | CSV preview creates no `FinancialTransaction` rows                                                                                                                                                                 | **Done**     |
| No Rule Engine in I1     | CSV preview does not run Rule Engine                                                                                                                                                                               | **Done**     |
| Preview status semantics | `IngestionRecord.status = VALID` means valid preview row, ready for future confirm import                                                                                                                          | **Done**     |
| Preview record link      | `IngestionRecord.financialTransaction` must be `null` for every I1/I2A preview row, including `VALID` rows                                                                                                         | **Done**     |
| Imported status          | `IMPORTED` is reserved for rows that generated a `FinancialTransaction` in a later confirm-import slice                                                                                                            | **Deferred** |
| Disabled status          | `DISABLED` keeps the row for audit/review and excludes it from future confirm import; top-level blocking errors are cleared, rawData remains traceable                                                             | **Done**     |
| Disable review action    | `VALID`/`REJECTED` rows can become `DISABLED`; `IMPORTED`/`SKIPPED_DUPLICATE`/`FAILED` are not review-editable in I2B                                                                                              | **Done**     |
| Enable review action     | `DISABLED` rows revalidate current normalized values; valid rows become `VALID`, invalid rows become `REJECTED`                                                                                                    | **Done**     |
| Edit review action       | Only `VALID`/`REJECTED` rows can edit normalized values; edit revalidates and returns `VALID` or `REJECTED`; `DISABLED` rows must be enabled before editing; `IMPORTED`/`SKIPPED_DUPLICATE`/`FAILED` are immutable | **Done**     |
| Edit derived fields      | Review edit never trusts client `amount`, `flow`, or `status`; `amount` and `flow` are derived from `signedAmount`                                                                                                 | **Done**     |
| Persisted review page    | `/transaction-ingestion/{id}/file-preview` loads persisted rows and read-only `FileIngestion` metadata; FileIngestion CRUD is not the product flow                                                                 | **Done**     |
| Raw/normalized payload   | Store raw row, normalized values, errors, and warnings in `IngestionRecord.rawData` JSON; row edit keeps `rawData.raw` unchanged and replaces `rawData.normalized`                                                 | **Done**     |
| Rejected uploads         | Invalid header, missing/empty/header-only/unreadable/oversized file creates nothing in I1; persisted `FAILED` ingestion for rejected upload deferred                                                               | **Done**     |
| Duplicate file checksum  | Same checksum/account is warning-only in I1; it does not block preview                                                                                                                                             | **Done**     |
| Minimal preview UI       | TransactionIngestion “New File Import” workflow selects account, uploads CSV, shows persisted preview summary/warnings/rows, and has no confirm/import action                                                      | **Done**     |

### Canonical CSV transaction rules

| Rule                 | Decision                                                                                         | Status   |
| -------------------- | ------------------------------------------------------------------------------------------------ | -------- |
| Contract             | Header must be exact, ordered, and case-sensitive                                                | **Done** |
| Sign convention      | Positive `signedAmount` → `flow = IN`                                                            | **Done** |
| Sign convention      | Negative `signedAmount` → `flow = OUT`                                                           | **Done** |
| Amount normalization | Preview `amount = abs(signedAmount)`; I2 will use this when creating `FinancialTransaction` rows | **Done** |
| Zero amount          | `signedAmount = 0` is invalid, not skipped                                                       | **Done** |
| Currency             | Row `currency` must match selected account currency                                              | **Done** |
| Account type         | CSV sign convention is canonical; do not infer flow from bank/account type/kind                  | **Done** |

### I2 — confirm import

| Rule                   | Decision                                                                                                       | Status       |
| ---------------------- | -------------------------------------------------------------------------------------------------------------- | ------------ |
| Confirm import         | Create `FinancialTransaction` rows from valid preview records                                                  | **Planned**  |
| Origin                 | Imported transactions use `origin = FILE_IMPORT`                                                               | **Planned**  |
| Rule Engine            | Explicitly apply Rule Engine `FILL_EMPTY_ONLY` during confirm import unless a later design finds a clear issue | **Planned**  |
| Evaluation persistence | Do not persist Rule Engine evaluation results in I2 unless separately designed                                 | **Deferred** |

---

## 17. FinancialAccount

**Pattern:** A. **Orchestrator** — most complex; implement **last**.

### DELETE — orchestrated

| #   | Step                                                       | Owner                     |
| --- | ---------------------------------------------------------- | ------------------------- |
| 1   | Resolve accessible account                                 | `FinancialAccountService` |
| 2   | `transactionIngestionService.deleteAllForAccount(account)` | TI service                |
| 3   | `financialTransactionService.deleteAllForAccount(account)` | FT service                |
| 4   | Unlink `Budget.accounts` (keep budgets)                    | FA service                |
| 5   | `FinancialSubscription.account = null`                     | FA service                |
| 6   | Delete `CreditAccountDetails`                              | FA service                |
| 7   | Delete `FinancialAccount`                                  | FA service                |

**Confirmed:** allowed even with txs + ingestions; cleans up via delegation. Single transaction.

### UPDATE / PATCH

| Rule                                                     | Decision                                                                                                              | Status   |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | -------- |
| `currency` immutable                                     |                                                                                                                       | **Done** |
| `accountType` immutable                                  |                                                                                                                       | **Done** |
| `createdAt` / `updatedAt` server-owned                   | Create ignores client values and sets both to `now`                                                                   | **Done** |
| PUT / PATCH `createdAt`                                  | Preserve existing; explicit null or changed value → `400 invalid`; same value allowed as no-op                        | **Done** |
| PUT / PATCH `updatedAt`                                  | Explicit null or changed value → `400 invalid`; same value allowed, then server sets `updatedAt = now`                | **Done** |
| `initialBalance` mutable                                 |                                                                                                                       | **Done** |
| `initialBalance` monetary scale                          | Required; positive, zero, and negative values allowed; `scale <= 2`; no rounding                                      | **Done** |
| `initialBalanceDate` floor `<= earliest transactionDate` | No floor when there are zero transactions; uses `transactionDate`, not `postingDate`; validates final PUT/PATCH state | **Done** |
| `active` mutable                                         | No side effects; does not delete/unlink/block imports                                                                 | **Done** |

**UI alignment:** FinancialAccount create/edit does not expose `createdAt` or `updatedAt` inputs. Edit mode treats `currency` and `accountType` as immutable selects. In create mode, changing `accountType` resets only `initialBalance` and `initialBalanceDate`; unrelated form fields are preserved.

### `initialBalance` semantics

`FinancialAccount.initialBalance` is the opening position at the beginning of tracking (`posición inicial`). Its meaning depends on `accountType` and sign. It is not always an available balance and it is not always debt.

| Account type  | Positive `initialBalance`  | Zero                        | Negative `initialBalance`                              | Future formula                                                 |
| ------------- | -------------------------- | --------------------------- | ------------------------------------------------------ | -------------------------------------------------------------- |
| `DEBIT`       | Starting available balance | No balance                  | Overdraft / negative balance                           | `currentBalance = initialBalance + IN - OUT`                   |
| `CASH`        | Starting cash on hand      | No cash recorded            | Adjustment / negative cash position                    | `currentBalance = initialBalance + IN - OUT`                   |
| `CREDIT_CARD` | Outstanding debt           | No debt / no credit balance | Credit balance / saldo a favor                         | `currentDebt = initialBalance + OUT - IN`                      |
| `INVESTMENT`  | Starting account value     | No value recorded           | Advanced/adjustment case; investment modeling deferred | `currentBalance = initialBalance + IN - OUT`, provisional only |

For `CREDIT_CARD`, `initialBalance` is not `creditLimit` and is not available credit. It represents the card's opening position. A positive value is debt; a negative value is saldo a favor. `CreditAccountDetails.creditLimit` is used later to calculate `availableCredit`.

`initialBalance` is required. Positive, zero, and negative values are allowed and meaningful. Service validates monetary scale (`scale <= 2`) on CREATE, PUT, and final merged PATCH state. Values with more than two decimal places are rejected with `400 invalid`; they are not rounded. Non-negative validation is not a rule.

`initialBalanceDate` is the date of the opening position and the start of tracking. If transactions exist, it must be `<=` earliest `FinancialTransaction.transactionDate`; use `transactionDate`, not `postingDate`. This validation is implemented.

### Balance read model

`GET /api/financial-accounts/{id}/balance` returns a calculated snapshot. Balances are not persisted and this pass does not add JDL/Liquibase fields.

| Rule                 | Decision                                                                                                              | Status   |
| -------------------- | --------------------------------------------------------------------------------------------------------------------- | -------- |
| Endpoint access      | Resolve account through FinancialAccount ownership rules; normal users get `404` for foreign accounts; admin can read | **Done** |
| `asOfDate`           | Optional; defaults to current date in service                                                                         | **Done** |
| Transaction basis    | Include account transactions by `transactionDate`, not `postingDate`                                                  | **Done** |
| Date range           | Inclusive `initialBalanceDate` through `asOfDate`                                                                     | **Done** |
| Inactive accounts    | Still calculate; `active=false` is not a delete/archive                                                               | **Done** |
| No transactions      | Return opening-position snapshot with zero inflow/outflow                                                             | **Done** |
| `DEBIT` / `CASH`     | `currentBalance = initialBalance + IN - OUT`                                                                          | **Done** |
| `CREDIT_CARD`        | `currentDebt = initialBalance + OUT - IN`; `availableCredit = creditLimit - currentDebt` when limit exists            | **Done** |
| Missing card details | Return `missingCreditDetails = true`; do not fail                                                                     | **Done** |
| `INVESTMENT`         | Provisional `currentBalance = initialBalance + IN - OUT`; valuation deferred                                          | **Done** |

### Product rules

| Rule                                           | Decision                                     | Status       |
| ---------------------------------------------- | -------------------------------------------- | ------------ |
| `currentBalance` read model                    | Calculated backend snapshot; not persisted   | **Done**     |
| Balance recalculation                          | No persisted recalculation in this pass      | **Deferred** |
| Monetary scale validation for `initialBalance` | `scale <= 2`; no rounding; negatives allowed | **Done**     |

---

## How to extend

1. Add rows under the entity section → **Proposed** / **Open**.
2. Implement in roadmap order; delegation for Grupo 3 deletes.
3. IT + service tests per rule → **Done**.
4. Update [`IMPLEMENTATION.md`](IMPLEMENTATION.md) domain-rules column.

---

_Last updated: 2026-07-13 — FinancialAccount backend-only balance read model marked complete; UI/dashboard display, persisted balances, statement cycles, and investment valuation remain deferred._
