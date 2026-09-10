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

| #   | Entity                       | Pattern                                                | Phase | Ownership | Domain rules  | Validations | Tests                                                                                                |
| --- | ---------------------------- | ------------------------------------------------------ | ----- | --------- | ------------- | ----------- | ---------------------------------------------------------------------------------------------------- |
| 1   | **FinancialAccount**         | A — direct `user`                                      | 1     | ✅        | ✅            | ✅          | [TESTING.md § FA](TESTING.md#financialaccount) · [VALIDATIONS §1](VALIDATIONS.md#1-financialaccount) |
| 2   | **FinancialTransaction**     | B — via `account`                                      | 2     | ✅        | ✅            | ✅          | [TESTING.md § FT](TESTING.md#financialtransaction)                                                   |
| —   | **TransactionCandidate**     | A + links — direct `user`, optional account/ingestion  | TC-1  | ✅        | ✅ foundation | ✅          | [TESTING.md § TC](TESTING.md#transactioncandidate)                                                   |
| 3   | **CreditAccountDetails**     | B — via `account`                                      | 4     | ✅        | ✅            | ✅          | [TESTING.md § CAD](TESTING.md#creditaccountdetails)                                                  |
| 4   | **Category**                 | A — direct `user`                                      | 3     | ✅        | ✅            | ✅          | [TESTING.md § Category](TESTING.md#category)                                                         |
| 5   | **Tag**                      | A — direct `user`                                      | 3     | ✅        | ✅            | ✅          | [TESTING.md § Tag](TESTING.md#tag) · [VALIDATIONS §5](VALIDATIONS.md#5-tag)                          |
| 6   | **TransactionRule**          | A — direct `user`                                      | 3 / 6 | ✅        | ✅            | ✅          | [TESTING.md § TransactionRule](TESTING.md#transactionrule)                                           |
| 7   | **TransactionRuleCondition** | C — via `transactionRule`                              | 6     | ✅        | ✅            | ✅          | [TESTING.md § TRC](TESTING.md#transactionrulecondition)                                              |
| 8   | **FinancialSubscription**    | A + links                                              | 3 / 4 | ✅        | ✅            | ✅          | [TESTING.md § FinancialSubscription](TESTING.md#financialsubscription)                               |
| 9   | **Budget**                   | A + M2M                                                | 3 / 4 | ✅        | ✅            | ✅          | [TESTING.md § Budget](TESTING.md#budget)                                                             |
| 10  | **InternalTransfer**         | D — via 2 transactions                                 | 4     | ✅        | ✅            | ✅          | [TESTING.md § IT](TESTING.md#internaltransfer)                                                       |
| 11  | **TransactionIngestion**     | B — via `account`                                      | 6     | ✅        | ✅            | ✅          | [TESTING.md § TI](TESTING.md#transactioningestion)                                                   |
| 12  | **FileIngestion**            | C — via `transactionIngestion`                         | 6     | ✅        | ✅            | ✅          | [TESTING.md § FI](TESTING.md#fileingestion)                                                          |
| 13  | **ApiIngestion**             | C — via `transactionIngestion` + token snapshots (11C) | 6     | ✅        | ✅ (11C)      | ✅          | [TESTING.md § AI](TESTING.md#apiingestion)                                                           |
| 14  | **IngestionRecord**          | C — via `transactionIngestion`                         | 6     | ✅        | ✅            | ✅          | [TESTING.md § IR](TESTING.md#ingestionrecord) · [VALIDATIONS §14](VALIDATIONS.md#14-ingestionrecord) |
| 15  | **ApiAccessToken**           | A — direct `user`                                      | 3 / 6 | ✅        | ✅ (11C)      | ✅          | [TESTING.md § AAT](TESTING.md#apiaccesstoken)                                                        |
| 16  | **ApiAccessTokenPermission** | C — via `apiAccessToken`                               | 6     | ✅        | ✅            | ✅          | [TESTING.md § AATP](TESTING.md#apiaccesstokenpermission)                                             |
| 17  | **UserDashboardPreference**  | A — direct `user` (1:1 user)                           | 3     | ✅        | ✅            | ✅          | [TESTING.md § UDP](TESTING.md#userdashboardpreference)                                               |

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

**TransactionCandidate exception:** TC-1/TC-2A treats candidates as product-owned draft/review state. Admin does not get special cross-user product behavior for candidate CRUD or manual commands; candidate operations resolve the current authenticated owner and validate every linked account/category/tag/ingestion record against that owner. The eventual `financialTransaction` link is server-controlled and is set only by explicit posting/conversion commands.

## TransactionCandidate — TC-1/TC-3C.2 draft/review foundation

`TransactionCandidate` is the central in-progress transaction model. It is intentionally separate from `FinancialTransaction`, which remains posted/final ledger data that affects balances, dashboards, budgets, and reports.

TC-1/TC-1A adds the backend foundation:

- JDL/.jhipster metadata, Liquibase table, backend entity/DTO/mapper/repository/service/resource.
- Direct owner via `user`.
- Optional links to `FinancialAccount`, `Category`, `Tag`, `TransactionIngestion`, and `IngestionRecord`; the optional `FinancialTransaction` link is readable but server-controlled/write-rejected in normal CRUD.
- Lifecycle/status foundation for manual drafts and file/API ingestion review. Bank sync is deferred and is not an active TC-1A source value.
- Server-owned timestamps, derived `amount`/`flow`, server-owned review statuses, and same-owner validations.
- `TransactionCandidateSource` describes how a candidate entered draft/review. `TransactionOrigin` describes final posted `FinancialTransaction` classification. They are not interchangeable; TC-1A does not store `TransactionOrigin` on `TransactionCandidate`.
- Future posting mapping: `MANUAL → MANUAL`, `FILE_IMPORT → FILE_IMPORT`, `API_IMPORT → API`. Bank sync remains unsupported until the final transaction origin model supports it.

TC-2A / TC-2A.1 adds manual command endpoints:

- `POST /api/transaction-candidates/manual` creates a current-user `MANUAL` draft.
- `PATCH /api/transaction-candidates/{id}/manual-draft` autosaves editable manual draft fields and recalculates DRAFT vs READY_TO_POST.
- `POST /api/transaction-candidates/{id}/cancel` marks a non-final manual draft CANCELLED and sets `cancelledAt`.
- `POST /api/transaction-candidates/{id}/post` loads the candidate with a pessimistic write lock, recalculates normalized/derived transaction fields from the current draft, converts a complete valid manual draft into exactly one posted `FinancialTransaction`, sets `origin=MANUAL`, links it back to the candidate, and is idempotent/concurrency-safe after POSTED.

Candidate post intentionally persists the `FinancialTransaction` through an internal controlled path, not through `FinancialTransactionService.save()`, so it does not secretly re-run `TransactionRuleEvaluationService`. Backend post now requires candidate classification review to be complete: `classificationReviewStatus` must be `SUGGESTED`, `USER_SELECTED`, or `NOT_APPLICABLE`; `NOT_EVALUATED` and `STALE` are rejected.

Generic `TransactionCandidate` CRUD write endpoints remain technical/restricted compatibility surfaces. They cannot create `FILE_IMPORT`/`API_IMPORT` candidates, cannot directly change lifecycle status, cannot set lifecycle/review/server-controlled fields, cannot set `financialTransaction`, cannot write derived `amount`/`flow`, preserve source immutability, and cannot delete `POSTED`/`CANCELLED` candidates. Manual lifecycle changes must go through the manual command endpoints.

TC-2D.2 adds the manual draft recovery page on top of the TC-2D.1 backend query:

- `GET /api/transaction-candidates/manual-drafts` returns lightweight summaries for the current user's recoverable `MANUAL` candidates.
- Included statuses are `DRAFT` and `READY_TO_POST`.
- Excluded statuses/sources are `POSTED`, `CANCELLED`, `FAILED`, `FILE_IMPORT`, and `API_IMPORT`.
- `NEEDS_REVIEW` is excluded because the current manual flow does not produce it; it remains deferred until explicit product semantics are added.
- Results are sorted by `updatedAt DESC, id DESC`.
- `/financial-transaction/drafts` loads the recovery summaries, displays compact draft/account/date/amount/status/classification/category/tags metadata, and provides Resume plus Cancel draft actions.
- Resume navigates to `/financial-transaction/drafts/{id}`. Cancel calls the candidate cancel command; the list does not post candidates.
- The FinancialTransaction list links to the recovery page with a secondary "View drafts" action, but draft rows are not mixed into the posted FinancialTransaction table.
- The endpoint/page do not expose generic `TransactionCandidate` CRUD as product UI.

TC-3A adds backend-only FILE import candidate preparation:

- `POST /api/transaction-ingestions/{id}/candidates/prepare` creates or synchronizes one `FILE_IMPORT` `TransactionCandidate` for each `VALID` `IngestionRecord` in an owned FILE `TransactionIngestion`.
- The command is allowed only for `READY` and `PARTIALLY_READY` FILE ingestions; `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, non-FILE, foreign, and missing-account ingestions are rejected.
- Non-`VALID` rows (`REJECTED`, `DISABLED`, `IMPORTED`, `SKIPPED_DUPLICATE`, `FAILED`) are skipped.
- New candidates are `READY_TO_POST`, `validationStatus=VALID`, `classificationReviewStatus=NOT_EVALUATED`, `source=FILE_IMPORT`, linked to the parent `TransactionIngestion` and source `IngestionRecord`, and mapped from `rawData.normalized`.
- Existing non-posted candidates are synced from `rawData.normalized`, preserving selected category/tags. If rule-input fields changed after a fresh classification (`SUGGESTED`, `USER_SELECTED`, `NOT_APPLICABLE`), classification is marked `STALE`. Notes-only changes do not mark stale.
- Existing `POSTED` candidates are skipped and not modified. `CANCELLED`/`FAILED` candidates are reported as row errors and are not resurrected.
- Prepare is idempotent, returns created/updated/unchanged/skipped/error counts plus row results, does not mutate `rawData`, does not store category/tags in `rawData`, and does not create `FinancialTransaction` rows.

TC-3B exposes prepared FILE import candidates in the workflow read model:

- `GET /api/transaction-ingestions/{id}/workflow` remains read-only and does not create/sync candidates.
- Each workflow row may include an optional lightweight `candidate` summary when a prepared `TransactionCandidate` exists for that `IngestionRecord`.
- Candidate summaries are loaded in one batch for the current-user owned `TransactionIngestion` and mapped by `ingestionRecord.id`; the workflow does not do one candidate lookup per row.
- The row summary exposes scalar candidate fields plus lightweight account/category/tag ids and names. It does not expose full nested `TransactionCandidateDTO` graphs.
- Rows without prepared candidates keep `candidate = null`/absent for JSON compatibility.
- Candidate data is still produced by the TC-3A prepare endpoint before it appears in the workflow response.

TC-3C.1 adds FILE import candidate classification commands:

- `PATCH /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/classification` stores reviewed category/tags on a prepared `FILE_IMPORT` candidate and marks classification `USER_SELECTED`. The request must include at least one of `categoryId` or `tagIds`; omitted fields preserve existing values.
- `POST /api/transaction-ingestions/{ingestionId}/candidates/rule-preview` evaluates current candidate state read-only using `TransactionOrigin.FILE_IMPORT`.
- `POST /api/transaction-ingestions/{ingestionId}/candidates/apply-rules` re-evaluates current candidate state and applies category/tags with `FILL_EMPTY_ONLY`.
- `POST /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/confirm-no-suggestions` marks `NOT_APPLICABLE` only when fresh evaluation has no suggestions.
- Commands are ingestion-scoped, current-user scoped, limited to `FILE_IMPORT` candidates linked to `VALID` records, and reject final candidates.
- Category/tags are persisted on `TransactionCandidate`; no category/tag selections or rule evaluation results are written to `IngestionRecord.rawData`.
- These endpoints do not create `FinancialTransaction` rows and do not change Confirm Import.

TC-3C.2 adds candidate-backed Pantalla 2 for FILE ingestion:

- The review UI calls `POST /api/transaction-ingestions/{id}/candidates/prepare`, reloads `GET /api/transaction-ingestions/{id}/workflow`, and requires every `VALID` row to have a usable `FILE_IMPORT` candidate before entering Pantalla 2.
- Pantalla 2 uses the workflow row `candidate` summary as the source of truth for selected category, tags, and classification review status.
- Batch preview calls `POST /api/transaction-ingestions/{id}/candidates/rule-preview` and keeps suggestions transiently keyed by candidate id.
- Manual category/tag edits call `PATCH /api/transaction-ingestions/{id}/candidates/{candidateId}/classification`.
- Per-row apply calls `POST /api/transaction-ingestions/{id}/candidates/apply-rules` with the selected candidate id and persists `FILL_EMPTY_ONLY` results on the candidate.
- Per-row no-suggestion confirmation calls `POST /api/transaction-ingestions/{id}/candidates/{candidateId}/confirm-no-suggestions`.
- Browser refresh reloads persisted candidate category/tag selections through the workflow row candidate summaries.
- Confirm Import still calls the existing `POST /api/transaction-ingestions/{id}/confirm` path. Before confirm, the UI reloads the workflow and validates every `VALID` candidate is `READY_TO_POST` and classification-reviewed. TC-3D.2 current frontend sends no legacy `records`/category/tag payload; TC-3D.1 backend confirm uses persisted `FILE_IMPORT` candidates as the source of truth.

Still unchanged after TC-3D.1:

- Manual `POST /api/financial-transactions` still creates posted `FinancialTransaction` directly.
- FinancialTransaction update/PATCH/rule-preview behavior is unchanged.
- CSV ingestion still uses `IngestionRecord.rawData.normalized` for Pantalla 1; Pantalla 2 category/tag review now persists decisions on `FILE_IMPORT` candidates.
- Confirm Import backend internals now create `FinancialTransaction` rows from reviewed `FILE_IMPORT` candidate fields/category/tags. TC-4B removes backend parsing/validation of the old confirm selection payload; persisted candidates are the source of truth.
- No UserPreference, re-evaluation buttons, unified review screen, draft dashboard/menu entry, or CSV/API TransactionCandidate product UI exists yet.

TC-2C.1b/1d adds the frontend rule suggestions slice for manual candidates:

- `/financial-transaction/new` and `/financial-transaction/drafts/{id}` expose a Rule suggestions section after a `MANUAL` candidate exists.
- Successful autosave of rule-input fields automatically calls `POST /api/transaction-candidates/{id}/rule-preview`; it renders transient suggestions/conflicts/matched rules and does not mutate category/tags.
- Notes-only and category/tag-only edits do not auto-preview.
- Apply suggestions / Confirm no suggestions flushes pending autosave and calls `POST /api/transaction-candidates/{id}/apply-rules`; the form hydrates category/tags/status from the returned candidate.
- Preview remains read-only; suggestions are not auto-applied.
- The Post button is blocked in the UI while `classificationReviewStatus` is `NOT_EVALUATED` or `STALE`.
- Post remains allowed for `SUGGESTED`, `USER_SELECTED`, and `NOT_APPLICABLE` when the candidate is otherwise `READY_TO_POST`.
- TC-2C.1c adds the same guard to backend `postManualDraft`, so direct API calls cannot bypass classification review.
- Candidate post still does not call preview/apply and does not secretly re-run `TransactionRuleEvaluationService`.
- Candidate UI does not call the public `POST /api/financial-transactions/rule-preview` endpoint.

TC-2B.1 adds the manual TransactionCandidate product UI:

- `/financial-transaction/new` now renders a manual candidate autosave form instead of the posted `FinancialTransaction` edit form.
- Page load does not create an empty candidate.
- First meaningful user change creates a recoverable `MANUAL` candidate through `POST /api/transaction-candidates/manual`.
- Meaningful first changes are account, transaction date, nonblank description, signed amount/amount entry, category, or tags; posting date, external reference, and notes alone are not enough to create the first candidate.
- After candidate creation, the URL is replaced with `/financial-transaction/drafts/{id}`.
- `/financial-transaction/drafts/{id}` is a MANUAL-only product route; non-MANUAL candidates and load failures render a safe error state with no editable form or manual draft actions.
- Subsequent edits debounce autosave through `PATCH /api/transaction-candidates/{id}/manual-draft`.
- There is intentionally no explicit Save Draft button.
- The UI displays amount plus flow for usability but sends only `signedAmount`; backend derives `amount` and `flow`.
- Posting flushes pending autosave, calls `POST /api/transaction-candidates/{id}/post`, and redirects to the posted `FinancialTransaction` detail page.
- Cancelling before a candidate exists just navigates away; cancelling a persisted candidate calls `POST /api/transaction-candidates/{id}/cancel`.
- TC-2B.1 does not call `POST /api/financial-transactions/rule-preview`, does not run frontend-side rules, and does not apply TransactionRules during candidate post.

TC-2C.1a adds backend-only candidate rule commands:

- `POST /api/transaction-candidates/{id}/rule-preview` evaluates active owner `TransactionRule`s against the current persisted MANUAL candidate and returns transient category/tag suggestions, matched rules, conflicts, and skipped outputs. It does not mutate the candidate.
- `POST /api/transaction-candidates/{id}/apply-rules` re-evaluates from current persisted candidate state and applies suggestions with `FILL_EMPTY_ONLY`: category only fills when empty/no conflict; tags are additive; manual category/tags are preserved.
- Both commands reject non-MANUAL and final `POSTED`/`CANCELLED`/`FAILED` candidates and use the candidate owner/current user scope. They do not call `POST /api/financial-transactions/rule-preview`.
- `PATCH /api/transaction-candidates/{id}/manual-draft` marks category/tag changes as `classificationReviewStatus=USER_SELECTED`; rule-input changes after a fresh classification mark `classificationReviewStatus=STALE`. Notes-only changes do not mark stale because TransactionRules do not evaluate notes.
- `apply-rules` sets `classificationReviewStatus=SUGGESTED` when suggestions exist for an unclassified candidate, `NOT_APPLICABLE` when no applicable suggestions exist, and preserves `USER_SELECTED` when manual category/tags already existed.
- TC-2C.1c hardens manual post: `NOT_EVALUATED` and `STALE` classification review states are rejected before a `FinancialTransaction` is created. Posting still does not run preview/apply secretly.

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

**Archivos:** `TransactionRuleRepository`, `TransactionRuleService`, `TransactionRuleConfigurationService`, `TransactionRuleConditionValidator`, `TransactionRuleFlowCategoryCompatibilityValidator`, `TransactionRuleQueryService`, `TransactionRuleResource` (PATCH con `JsonNode` + configured endpoints), configured DTOs, `TransactionRuleDTO`, `TransactionRuleMapper`, UI.

#### Domain rules ✅ (CRUD/domain baseline)

| Regla                                           | Estado | Notas                                                                                                                                                                                                           |
| ----------------------------------------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Usuario no puede ver/editar rule ajena          | ✅     | Pattern A                                                                                                                                                                                                       |
| No cambiar dueño en update/patch                | ✅     |                                                                                                                                                                                                                 |
| Admin accede a todo (CRUD rule)                 | ✅     |                                                                                                                                                                                                                 |
| Outputs ⊆ dueño de la rule (aunque admin edite) | ✅     | No mezclar owners rule ↔ category/tag                                                                                                                                                                          |
| PATCH: omitir link preserva; `null`/`[]` limpia | ✅     | `JsonNode` en resource                                                                                                                                                                                          |
| Condiciones hijas scoped al rule                | ✅     | TransactionRuleCondition — pattern C                                                                                                                                                                            |
| Normalización + unicidad de nombre              | ✅     | trim; uniqueness per owner, case-insensitive/trim-insensitive; inactive reserves name                                                                                                                           |
| Description normalization                       | ✅     | trim; blank → `null`                                                                                                                                                                                            |
| Server-owned timestamps                         | ✅     | create sets both; PUT/PATCH reject explicit null/changed `createdAt`/`updatedAt`; successful update sets `updatedAt=now`                                                                                        |
| Server-managed priority/order                   | ✅     | per-user 0-based consecutive ordering; create appends; update/patch preserve; delete reindexes same owner only                                                                                                  |
| Active/configured rule requiere conditions      | ✅     | product create/edit saves complete configured rules; detail/debug child endpoints can still deactivate parent when last condition is deleted                                                                    |
| Configured command API                          | ✅     | `POST /api/transaction-rules/configured`, `GET /api/transaction-rules/{id}/configured`, `PUT /api/transaction-rules/{id}/configured` manage parent + ordered conditions through dedicated DTOs                  |
| Configured PUT full child replacement           | ✅     | Preserves parent `priority`/`createdAt`, updates `updatedAt`, replaces the full condition collection, and server-assigns positions                                                                              |
| Resulting category ↔ FLOW compatibility        | ✅     | Active/configured EXPENSE rules require `ALL` + effective `FLOW=OUT`; INCOME requires `ALL` + effective `FLOW=IN`; BOTH/tag-only/no-category rules do not require FLOW                                          |
| Rule requiere al menos un output                | ✅     | final merged state                                                                                                                                                                                              |
| Delete cleanup                                  | ✅     | conditions + resultingTags join; no output entities deleted                                                                                                                                                     |
| PUT contract                                    | ✅     | Full DTO update; not presence-aware partial semantics. PATCH remains JsonNode                                                                                                                                   |
| Parent-centered conditions endpoint             | ✅     | `GET /api/transaction-rules/{id}/conditions`, scoped by parent access, sorted by `position,id`                                                                                                                  |
| Rule list ordering                              | ✅     | `TransactionRuleQueryService.findByCriteria` sorts filtered results by `priority ASC, id ASC`                                                                                                                   |
| Product configured create/edit UX               | ✅     | `/transaction-rule/new` and `/transaction-rule/:id/edit` use configured endpoints, local inline conditions, output selectors, condition logic, and active state in one form                                     |
| Configured create flow                          | ✅     | No empty draft rule; save requires name, at least one output, and at least one condition; active is editable and defaults true when the configured rule is valid                                                |
| Configured condition form                       | ✅     | Reuses TransactionRuleCondition smart form section/helper; parent selector hidden; local create/edit/delete does not call child endpoints                                                                       |
| Configured category/FLOW UX                     | ✅     | EXPENSE auto-adds/locks `FLOW=OUT`; INCOME auto-adds/locks `FLOW=IN`; BOTH/null removes only frontend auto-created FLOW; incompatible user FLOW blocks save; `ANY` blocked for EXPENSE/INCOME                   |
| Detail/debug condition mutation                 | ✅     | Detail embeds inline add/edit/delete via existing child endpoints; POST includes `transactionRule: { id }`; adding does not auto-activate; PATCH sends editable fields only; DELETE refreshes parent state      |
| Product-oriented rule UX                        | ✅     | List/detail use translated status, condition logic, output/result summaries, compact view/edit layout parity, and metadata                                                                                      |
| Priority/order UX                               | ✅     | List/detail show read-only 1-based order; list forces `priority ASC, id ASC`, exposes only possible Move up / Move down controls, and create/edit do not render or submit priority                              |
| Manual reorder endpoint                         | ✅     | `PUT /api/transaction-rules/reorder` accepts full current-user ordered ids, validates exact membership, and normalizes priorities to `0..n`                                                                     |
| Edit form hydration                             | ✅     | Edit waits for the requested entity before mounting the JHipster `ValidatedForm`; fields stay direct children for registration/defaults                                                                         |
| Rule Engine Phase 1 evaluator                   | ✅     | Backend-only pure evaluator in `service.rules`; internal `RuleEvaluationResult` supports category/tag suggestions only                                                                                          |
| **Ejecución al crear transaction**              | ✅     | Phase 2 applies rules on FinancialTransaction create with `FILL_EMPTY_ONLY` after resolving a current-user-accessible account; no admin override or cross-user evaluation; not restricted to `MANUAL` only      |
| Rule Engine draft workflow endpoint             | ✅     | Phase 3A exposes `POST /api/financial-transactions/rule-preview` for unsaved drafts; returns suggestions/conflicts/skips/matches; no save, mutation, UI, or persisted result                                    |
| TransactionCandidate manual autosave create UI  | ✅     | TC-2B.1 frontend create uses recoverable `MANUAL` candidates, creates on first meaningful change, autosaves to `/financial-transaction/drafts/{id}`, and posts through the candidate post command               |
| Candidate backend rule preview/apply commands   | ✅     | TC-2C.1a exposes candidate-specific preview/apply commands on `/api/transaction-candidates/{id}`; preview is transient, apply is FILL_EMPTY_ONLY, post does not rerun rules                                     |
| Candidate-specific rule-preview/apply UI        | ✅     | TC-2C.1d manual candidate UI auto-previews after saved rule-input changes, keeps apply/confirm explicit, uses candidate-specific endpoints, and blocks Post while classification is `NOT_EVALUATED` or `STALE`  |
| Candidate post classification backend guard     | ✅     | TC-2C.1c backend post rejects `NOT_EVALUATED` and `STALE`; only `SUGGESTED`, `USER_SELECTED`, and `NOT_APPLICABLE` can post when otherwise valid                                                                |
| Candidate manual draft recovery query/UI        | ✅     | TC-2D.2 exposes `GET /api/transaction-candidates/manual-drafts` plus `/financial-transaction/drafts` for current-user `MANUAL` `DRAFT`/`READY_TO_POST` summaries; resume/cancel only, no post from list         |
| FILE import candidate prepare/sync              | ✅     | TC-3A exposes `POST /api/transaction-ingestions/{id}/candidates/prepare`; creates/syncs `FILE_IMPORT` candidates for `VALID` rows only; idempotent; no rawData mutation; no FinancialTransaction creation       |
| FILE import candidate workflow summaries        | ✅     | TC-3B exposes optional lightweight prepared candidate summaries on `GET /api/transaction-ingestions/{id}/workflow`; read-only; no candidate creation                                                            |
| FILE import candidate classification backend    | ✅     | TC-3C.1 exposes ingestion-scoped candidate classification PATCH/preview/apply/confirm-no-suggestions endpoints; category/tags persist on candidates only; no rawData mutation; no FinancialTransaction creation |
| FILE import candidate-backed Pantalla 2         | ✅     | TC-3C.2 migrates TransactionIngestion category/tag review UI to candidate source of truth and persists selections on candidates; TC-3D.2 confirm sends no legacy selection payload                              |
| FILE import candidate-backed Confirm Import     | ✅     | TC-3D.1 backend Confirm Import posts reviewed `FILE_IMPORT` candidates as source of truth, links candidates/records/transactions, and preserves rawData; TC-4B removes old selection-body parsing               |
| Drag-and-drop reorder                           | ⏳     | Explicit drag-and-drop UX remains deferred; current implementation is button-based Move up / Move down                                                                                                          |

#### Validations ✅

| Capa                      | Estado      | Detalle                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ------------------------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| DTO `@Valid`              | ✅ JHipster | name, enums, dates; `priority` optional in request because service owns it; `user` opcional en payload                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| Service — links           | ✅          | Foreign output en create/update/patch → `400`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| Service — domain baseline | ✅          | Normalization, uniqueness, output requirement, active/conditions, PATCH null semantics, strict timestamp ownership, delete cleanup implemented                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| REST                      | ✅          | `@Valid` + `isAccessible` + `IllegalArgumentException` → `400`; cross-user PUT/PATCH → `400`, GET/DELETE → `404`; related conditions endpoint returns `404` when parent inaccessible                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| UI                        | ✅          | Product create/edit use configured parent+conditions endpoints with local inline condition state, output selectors, active state, and category/FLOW guard; list/detail use compact semantic summaries and read-only order; list Move up / Move down sends full ordered ids to the reorder endpoint; create/edit omit priority; detail still embeds TransactionRuleCondition editor via existing endpoints but TR-3 labels it technical/debug; standalone TransactionRuleCondition generated screens/menu are marked technical/debug; manual FinancialTransaction product create now uses TransactionCandidate autosave UI with automatic candidate rule preview, explicit Apply/Confirm actions, and post gating |

#### Temporary generated TransactionRule write/debug surfaces

The configured command endpoints are the product source of truth for TransactionRule create/edit:

- `POST /api/transaction-rules/configured`
- `GET /api/transaction-rules/{id}/configured`
- `PUT /api/transaction-rules/{id}/configured`

The older generated TransactionRule write endpoints and TransactionRuleCondition CRUD endpoints remain available temporarily for generated/debug/direct-maintenance compatibility. They are not the product workflow and should not be used to guide the normal “create empty rule, then add conditions later” flow. Backend strict validation still applies to those endpoints while they exist, including active-rule condition/output guards and category/FLOW compatibility. Ingestion category/tag review work is not part of this branch, and UserPreference remains deferred.

#### Rule Engine phases

See [`RULE-ENGINE.md`](RULE-ENGINE.md) for the full design contract.

1. ✅ Pure evaluator service returning `RuleEvaluationResult`; category/tag suggestions only; no mutation, no UI.
2. ✅ Apply on `FinancialTransaction` create using fill-empty-only behavior after resolving an account accessible to the current user and evaluating only that transaction/account owner's rules.
3. ✅ Backend-only draft workflow endpoint: `POST /api/financial-transactions/rule-preview`; no save/mutation/application.
4. ✅ TC-2B.1 manual TransactionCandidate autosave UI: create on first meaningful change, resume via `/financial-transaction/drafts/{id}`, autosave with no Save Draft button, and post through the candidate command.
5. ✅ TC-2C.1a backend candidate-specific rule preview/apply commands.
6. ✅ TC-2C.1b candidate-specific rule preview/apply UI and stricter post gating.
7. ✅ TC-2D.2 manual draft recovery query/UI.
8. Reevaluate one transaction.
9. Bulk reevaluation.

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

| Tema                                              | Estado      | Notas                                                                                                                                        |
| ------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Balances                                          | ⏳          | DELETE y create no recalculan saldos                                                                                                         |
| Create atómico out+in+transfer                    | ⏳          | Solo enlazar txs existentes en este PR                                                                                                       |
| Posting dates                                     | ⏳          | Sin validación de alineación entre patas                                                                                                     |
| FT delete cleanup                                 | ✅          | `FinancialTransactionService.delete()` elimina el vínculo InternalTransfer y preserva la contraparte                                         |
| Origin unrestricted for existing transaction legs | ✅ baseline | `MANUAL` / `FILE_IMPORT` / `API` are allowed; ingestion-created transfer pairing remains a future product decision                           |
| Admin transfer-leg lookup                         | ✅ diseño   | Admin lookup for existing transfer legs follows InternalTransfer rules; TransactionCandidate admin behavior has no cross-user product bypass |

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

**UI / product scope:** API_IMPORT product ingestion is deferred. ApiIngestion remains a technical/debug metadata surface only: list/detail are read-only with Technical marking, product Create/Edit/Delete affordances are hidden, and direct generated write routes show a safe unavailable state instead of generated forms. Backend generic writes remain temporarily available for technical/test compatibility, but canonical future API ingestion should use TransactionIngestion workflow commands.

**Fuera de scope:** pipeline, idempotency real, status machine, delete guards de hijos.

#### Refactor 11C ✅

- [x] JDL + `.jhipster/ApiIngestion.json`: quitar relación `apiAccessToken`; agregar campos snapshot
- [x] Entity: remover `apiAccessToken` FK; agregar snapshots
- [x] Liquibase `20260711160000`: drop `api_access_token_id` FK; add snapshot columns; backfill datos existentes
- [x] DTO/mapper: quitar `apiAccessToken`; exponer snapshots en read; usar `ApiIngestionCreateRequestDTO.apiAccessTokenId` solo en POST
- [x] Service: en create, normalizar strings, resolver token accesible por id → copiar snapshots; quitar persist FK; no mutable fields v1; direct delete blocked
- [x] `ApiAccessToken`: quitar `apiIngestions` one-to-many + `JsonIgnoreProperties`
- [x] UI: list/detail show snapshots read-only with Technical marking; product Create/Edit/Delete are hidden; direct generated write routes show unavailable technical state
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

> CSV Ingestion I2A status cleanup: the old `IngestionRecordStatus.CREATED` semantic was removed. CSV workflow now uses `VALID` for valid review rows with `financialTransaction = null`; `IMPORTED` is reserved for later confirm import rows that actually generate a `FinancialTransaction`.

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

#### I1B — persisted workflow endpoint

| Item               | Plan                                                                                                                                                                                                     |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Endpoint           | `POST /api/transaction-ingestions/file` multipart with `accountId` + `file` for canonical product creation; `GET /api/transaction-ingestions/{id}/workflow` loads canonical workflow detail/review data. |
| Parent upload      | `POST /api/transaction-ingestions/{id}/file-ingestion` multipart with `file` attaches CSV metadata/records to an existing owned `PENDING` FILE parent.                                                   |
| Ownership          | Resolve account ownership before creating anything. Admin has no special import bypass.                                                                                                                  |
| Parent             | Create `TransactionIngestion` with `ingestionType = FILE`.                                                                                                                                               |
| File child         | Create `FileIngestion` metadata with `fileType = CSV`, `parserName = fintrack-canonical-csv`, `parserVersion = 1.0`, SHA-256 checksum, `storageKey = null`.                                              |
| Records            | Create one `IngestionRecord` per CSV data row. Store raw/normalized/errors/warnings in `rawData` JSON.                                                                                                   |
| Status             | Valid review rows use `IngestionRecordStatus.VALID`; `CREATED` is no longer used.                                                                                                                        |
| FT link            | Every I1 `IngestionRecord.financialTransaction` remains `null`.                                                                                                                                          |
| Counters           | `recordsReceived = data rows`; `recordsCreated = 0`; `recordsRejected = invalid rows`; `recordsSkipped = 0`.                                                                                             |
| Dates              | `FileIngestion.statementStartDate/statementEndDate` derive from min/max `transactionDate` among valid rows.                                                                                              |
| Rejected upload    | Invalid header, missing file, empty file, header-only file, unreadable file, and oversized file create nothing in I1.                                                                                    |
| Duplicate checksum | Same checksum/account is warning-only in I1; it does not block upload/review.                                                                                                                            |
| Service            | `CsvIngestionWorkflowService`.                                                                                                                                                                           |
| Response           | Return persisted workflow DTO with summary counts, global warnings, and rows.                                                                                                                            |
| Not included       | No `FinancialTransaction` creation; no Rule Engine.                                                                                                                                                      |
| Tests              | `TransactionIngestionWorkflowResourceIT`.                                                                                                                                                                |

#### I1C — minimal UI

| Item     | Status                                                                                                                                                                                                     |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Entry    | ✅ Canonical TransactionIngestion FILE creation workflow at `/transaction-ingestion/new`, linked from the TransactionIngestion list as one clear New File Import action.                                   |
| Form     | ✅ In `/transaction-ingestion/new`, select Account + Ingestion Type and upload canonical CSV for `FILE`; API shows TBD and is not submittable. Generated FileIngestion write routes are unavailable in UI. |
| Redirect | ✅ Successful canonical upload redirects to recoverable `/transaction-ingestion/{id}` workflow detail/review page.                                                                                         |
| Result   | ✅ Show persisted workflow summary, read-only FileIngestion metadata, non-blocking warnings, no-transactions-created notice, and read-only row table.                                                      |
| Review   | ✅ Enable/disable row review actions and edit normalized review-row values.                                                                                                                                |
| Confirm  | ✅ Confirm Import appears on the persisted review page only when the recalculated status is `READY` and at least one `VALID` row exists.                                                                   |
| Shortcut | Deferred; later FinancialAccount detail shortcut should reuse the same flow with account preselected.                                                                                                      |
| Tests    | ✅ `transaction-ingestion-workflow-detail.spec.tsx`.                                                                                                                                                       |

#### Ingestion generated/debug UI cleanup

| Area                 | Status                                                                                                                                                                                         |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TransactionIngestion | ✅ List and workflow detail keep View/Delete/navigation as applicable but hide Edit; `/transaction-ingestion/{id}/edit` shows technical write-unavailable.                                     |
| FileIngestion        | ✅ Menu/list/detail remain technical/read-only; list/detail hide Create/Edit/Delete; generated write routes show technical write-unavailable.                                                  |
| IngestionRecord      | ✅ Menu/list/detail remain technical/read-only; list/detail hide Create/Edit/Delete; generated write routes show technical write-unavailable; row review actions live in TransactionIngestion. |
| Routes/back end      | ✅ No backend behavior, DB, JDL, Liquibase, confirm import, Rule Engine, or ApiIngestion behavior changed.                                                                                     |

#### Temporary generated ingestion write surfaces

| Surface              | Current status                                                                                                                                                                                           |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| TransactionIngestion | Generated `POST /api/transaction-ingestions`, `PUT /api/transaction-ingestions/{id}`, and `PATCH /api/transaction-ingestions/{id}` are marked technical/deprecated in code comments and remain writable. |
| FileIngestion        | Generated `POST /api/file-ingestions`, `PUT /api/file-ingestions/{id}`, and `PATCH /api/file-ingestions/{id}` are marked technical/deprecated in code comments and remain writable.                      |
| IngestionRecord      | Generated `POST /api/ingestion-records`, `PUT /api/ingestion-records/{id}`, and `PATCH /api/ingestion-records/{id}` are marked technical/deprecated in code comments and remain writable.                |
| Canonical workflow   | Product writes use TransactionIngestion workflow command endpoints; generated reducer thunks/tests may remain until generated technical routes are removed.                                              |
| Behavior             | No response codes, validation behavior, routes, redirects, backend commands, DB, JDL, Liquibase, Confirm Import, Rule Engine, or ApiIngestion behavior changed in this marking slice.                    |

#### I2B.2 — edit normalized review rows

| Item        | Status                                                                                                                                                                                                                            |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Endpoint    | ✅ `PATCH /api/transaction-ingestions/{ingestionId}/records/{recordId}` returns updated row + counts.                                                                                                                             |
| Editable    | ✅ `transactionDate`, `postingDate`, `description`, `signedAmount`, `currency`, `externalReference`, `notes`.                                                                                                                     |
| Derived     | ✅ `amount = abs(signedAmount)` and `flow = IN/OUT` are derived server-side; client-provided `amount`, `flow`, and `status` are ignored.                                                                                          |
| rawData     | ✅ `rawData.raw` remains original CSV data; `rawData.normalized`, `errors`, and `warnings` are recalculated from the edited normalized values; review edit metadata is stored when practical.                                     |
| Status      | ✅ Editing is allowed only for `VALID` and `REJECTED`, revalidates, and returns `VALID` or `REJECTED`; `DISABLED` rows must be enabled before editing; `IMPORTED`, `SKIPPED_DUPLICATE`, and `FAILED` rows are rejected.           |
| Counters    | ✅ Counts are recalculated after edit; `DISABLED` rows count as skipped and do not block readiness by themselves; `REJECTED`/`FAILED` rows or zero `VALID` rows make the batch `PARTIALLY_READY`; otherwise the batch is `READY`. |
| Status enum | ✅ `READY`/`PARTIALLY_READY` are pre-import review statuses; `COMPLETED`/`PARTIALLY_COMPLETED` are import-result statuses; `PARTIALLY_COMPLETED` is reserved and is not produced by CSV review.                                   |
| Not done    | ✅ No `FinancialTransaction` creation in review actions, no Rule Engine invocation, no ApiIngestion change, no CSV mapper, no PDF upload, no FinancialAccount shortcut.                                                           |

#### I2 — confirm import

| Item             | Status                                                                                                                                                                                                                                                                    |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Endpoint         | ✅ `POST /api/transaction-ingestions/{id}/confirm`.                                                                                                                                                                                                                       |
| Shared readiness | ✅ Preview, review actions, and confirm use the same readiness calculation: `READY` requires at least one `VALID` row and no `REJECTED`/`FAILED` rows.                                                                                                                    |
| Creation         | ✅ Creates `FinancialTransaction` rows from `VALID` review records only.                                                                                                                                                                                                  |
| Source payload   | ✅ Uses reviewed `FILE_IMPORT` `TransactionCandidate` fields/category/tags synced from `IngestionRecord.rawData.normalized` and Pantalla 2 classification decisions.                                                                                                      |
| Origin           | ✅ Imported transactions use `origin = FILE_IMPORT`.                                                                                                                                                                                                                      |
| Record link      | ✅ Each imported row becomes `IMPORTED` and links to its created `FinancialTransaction`.                                                                                                                                                                                  |
| Skipped rows     | ✅ `DISABLED` rows remain disabled/skipped and do not block readiness by themselves.                                                                                                                                                                                      |
| Parent status    | ✅ Successful confirm is all-or-nothing and marks the parent `COMPLETED`; retrying a completed import is idempotent and creates no duplicate transactions.                                                                                                                |
| Classification   | ✅ TC-3C.2 Pantalla 2 uses ingestion-scoped candidate preview/apply commands; TC-3D.1 Confirm Import requires reviewed candidate classification. TC-4B removes the old `classification-preview` endpoint/service/DTO path.                                                |
| Pantalla 2 UI    | ✅ READY workflows continue to a same-page category/tag review step; candidates are prepared, workflow is reloaded, suggestions are previewed transiently, and user decisions persist on candidates.                                                                      |
| Selections       | ✅ Current frontend does not send per-row category/tag selections to confirm. It validates persisted candidates first, then posts `/confirm` with no legacy `records` payload; backend confirm applies persisted candidate category/tags after ownership/flow validation. |
| Rule Engine      | ✅ CSV v1 confirm import does not invoke the Rule Engine itself and does not persist evaluation results or selections into `rawData`; UserPreference/AUTO_APPLY deferred.                                                                                                 |
| Counters         | ✅ Import counters are recalculated after confirm.                                                                                                                                                                                                                        |

TC-4B removes old ingestion classification surfaces: `POST /api/transaction-ingestions/{id}/classification-preview`, its service, and its DTOs are gone because the current frontend and backend import path use candidate-backed endpoints. Confirm Import no longer parses or validates the old `records/categoryId/tagIds` body; persisted `FILE_IMPORT` candidates remain the backend source of truth.

#### TC-3C.1 — FILE_IMPORT candidate classification backend

| Item                   | Status                                                                                                                                                                                                                                                                                                   |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Service                | ✅ `FileImportCandidateClassificationService` owns ingestion-scoped FILE candidate category/tag review commands.                                                                                                                                                                                         |
| Manual classification  | ✅ `PATCH /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/classification` requires at least one of `categoryId` or `tagIds`, preserves absent fields, clears explicit `null` category / empty tag lists, replaces provided tags, and sets `classificationReviewStatus=USER_SELECTED`. |
| Rule preview           | ✅ `POST /api/transaction-ingestions/{ingestionId}/candidates/rule-preview` evaluates current persisted candidate state read-only with `TransactionOrigin.FILE_IMPORT` and returns per-candidate suggestions, matched rules, conflicts, skipped outputs, and candidate summaries.                        |
| Apply rules            | ✅ `POST /api/transaction-ingestions/{ingestionId}/candidates/apply-rules` re-evaluates current candidate state and applies category/tags with `FILL_EMPTY_ONLY`: category fills only when empty/no conflict; tags are additive and deduplicated; manual selections are preserved.                       |
| Confirm no suggestions | ✅ `POST /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/confirm-no-suggestions` sets `NOT_APPLICABLE` only when fresh evaluation has no suggestions; it rejects candidates that still have suggestions to review.                                                                    |
| Guards                 | ✅ Endpoints require current-user owned FILE parent ingestion, `FILE_IMPORT` candidate source, non-final candidate status, linked `VALID` ingestion record, account presence, category/tag ownership, and category-flow compatibility.                                                                   |
| Persistence boundary   | ✅ Category/tags live on `TransactionCandidate`; `IngestionRecord.rawData` is not mutated and no category/tag selections or evaluation results are written to JSON.                                                                                                                                      |
| Import boundary        | ✅ These endpoints create no `FinancialTransaction` rows and do not change `CsvIngestionConfirmImportService` semantics.                                                                                                                                                                                 |
| UI boundary            | ✅ TC-3C.2 frontend uses these endpoints for Pantalla 2 candidate-backed category/tag review.                                                                                                                                                                                                            |

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

| Date       | Change                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 2026-07-08 | Initial tracker. FinancialAccount: ownership ✅, domain/validations 🟡. 16 entities ⏳.                                                                                                                                                                                                                                                                                                                                                                            |
| 2026-07-08 | FinancialTransaction: initial ownership ✅ (pattern B), initial domain baseline ✅ (manual create + amount > 0), validations ✅. Superseded by 2026-07-12 full backend domain pass.                                                                                                                                                                                                                                                                                |
| 2026-07-08 | Tag: ownership ✅ (pattern A, clon FA). Domain/validations 🟡. DTO `user` opcional en payload. Siguiente: Category.                                                                                                                                                                                                                                                                                                                                                |
| 2026-07-09 | Category: ownership ✅ (pattern A), domain rules 🟡 (parent owned, no self-parent, anti-ciclo ✅; delete guards ⏳), validations ✅. Siguiente: Budget.                                                                                                                                                                                                                                                                                                            |
| 2026-07-09 | Budget: ownership ✅ (pattern A + M2M), domain rules 🟡 (links owned ✅; empty-set semantics ⏳), validations ✅. Siguiente: FinancialSubscription.                                                                                                                                                                                                                                                                                                                |
| 2026-07-09 | FinancialSubscription: ownership ✅ (pattern A + links), domain rules 🟡 (links owned ✅; delete guards / matching ⏳), validations ✅. Siguiente: TransactionRule.                                                                                                                                                                                                                                                                                                |
| 2026-07-09 | FinancialSubscription: PATCH link semantics ✅ (`JsonNode` + `has(field)`); links owned en PUT/PATCH ✅. Convención HTTP cross-user documentada en Foundation.                                                                                                                                                                                                                                                                                                     |
| 2026-07-09 | TransactionRule: ownership ✅ (pattern A + links), domain rules 🟡 (outputs ⊆ rule owner, sin bypass admin en links; motor ⏳), validations ✅. Siguiente: TransactionRuleCondition.                                                                                                                                                                                                                                                                               |
| 2026-07-11 | **TransactionRuleCondition plan:** `transactionRule` immutable after create — **reparent same-owner removed**. Domain validations planned: field/operator matrices, duplicate guard, DELETE last condition → deactivate parent rule.                                                                                                                                                                                                                               |
| 2026-07-11 | **TransactionRuleCondition complete:** immutable parent, field/operator/value matrices, normalized duplicate guard, owner-scoped ACCOUNT ids, presence-aware PATCH null handling, and last-condition rule deactivation. Later hardened `position` as server-managed append order.                                                                                                                                                                                  |
| 2026-07-09 | TransactionRuleCondition: ownership ✅ (pattern C via `transactionRule`), validations ✅ (parent required, ~~reparent same-owner~~ → **immutable parent**). Domain rules 🟡 (field/operator/value, delete side-effect). Siguiente: implement TRC domain rules.                                                                                                                                                                                                     |
| 2026-07-09 | CreditAccountDetails: ownership ✅ (pattern B via `account`), validations ✅ (CREDIT_CARD only, immutable account, duplicate guard). Domain rules 🟡. Siguiente: ApiAccessToken.                                                                                                                                                                                                                                                                                   |
| 2026-07-09 | ApiAccessToken: ownership ✅ (pattern A), security baseline ✅ (no `tokenHash` in reads, immutable secrets). Domain rules 🟡. Siguiente: ApiAccessTokenPermission.                                                                                                                                                                                                                                                                                                 |
| 2026-07-09 | ApiAccessTokenPermission: ownership ✅ (pattern C via `apiAccessToken`), validations ✅ (immutable parent/grant, server `createdAt`, duplicate guard). Domain rules 🟡. Siguiente: UserDashboardPreference o InternalTransfer.                                                                                                                                                                                                                                     |
| 2026-07-09 | UserDashboardPreference: ownership ✅ (pattern A + 1:1 guard), validations ✅ (existsByUserId, PATCH user preserve/null). Domain rules 🟡. Siguiente: InternalTransfer.                                                                                                                                                                                                                                                                                            |
| 2026-07-11 | InternalTransfer domain rules ✅: origin unrestricted, notes normalization, strict createdAt/link PATCH semantics, no duplicate participation in either role, FT delete cleanup, candidate endpoints aligned. Balances/atomic create remain out of scope.                                                                                                                                                                                                          |
| 2026-07-09 | TransactionIngestion: modelo refactor ✅ — `account` ManyToOne required (reemplaza M2M `accounts`); pattern planificado **B** (no D). Ownership ⏳. Siguiente: ownership PR (14/17).                                                                                                                                                                                                                                                                               |
| 2026-07-09 | TransactionIngestion: ownership ✅ (pattern B via `account`), validations ✅ (inmutables, server defaults, scoped helpers). Domain rules ⏳ (pipeline). Siguiente: FileIngestion.                                                                                                                                                                                                                                                                                  |
| 2026-07-12 | TransactionIngestion domain rules ✅: lifecycle/status transitions, counter consistency, server-owned timestamps, source/error normalization, final child metadata guards, and explicit revert/delete cleanup order.                                                                                                                                                                                                                                               |
| 2026-07-11 | FileIngestion domain rules ✅: normalization, immutable file metadata, mutable statement dates with range guard, server-owned `createdAt`, direct delete blocked, and TransactionIngestion parent cleanup.                                                                                                                                                                                                                                                         |
| 2026-07-09 | ApiIngestion: ownership ✅ (pattern C + dual parent + same-owner), validations ✅ (parent API, 1:1 guard, `requestId` unique global, immutable parents/token/requestId/timestamps, server `createdAt`/`receivedAt`). Siguiente: IngestionRecord.                                                                                                                                                                                                                   |
| 2026-07-09 | IngestionRecord: ownership ✅ (pattern C + optional FT + same-owner), validations ✅ (immutable parents/recordIndex/createdAt, FT 1:1 guard, recordIndex unique, server `createdAt`, FT helper scoped). **17/17 ownership complete.** Superseded by 2026-07-12 domain-rule pass.                                                                                                                                                                                   |
| 2026-07-12 | IngestionRecord domain rules ✅: status consistency, parent final freeze, externalRecordId normalization/uniqueness, rawData immutability/log safety, direct delete blocked. 87 IT + 7 service + 1 FT helper IT. Pipeline/count reconciliation ⏳ fase 6.                                                                                                                                                                                                          |
| 2026-07-12 | FinancialTransaction domain rules ✅: JsonNode POST/PUT/PATCH, server timestamps, immutable account/origin/ingestion, owner-scoped links, category/subscription compatibility, internal-transfer guards, delete cleanup for IngestionRecord/InternalTransfer/tag joins. 101 IT + 10 service.                                                                                                                                                                       |
| 2026-07-12 | TransactionRule CRUD/domain baseline ✅: strict server-owned `createdAt`/`updatedAt`, PUT documented as full DTO update except server-managed priority preservation, PATCH remains JsonNode presence-aware, delete cleanup direct via repositories. Rule engine and batch reclassification remain deferred; manual Move up / Move down reorder added later.                                                                                                        |
| 2026-07-13 | FinancialAccount balance read model ✅: backend-only `GET /api/financial-accounts/{id}/balance`; strategy calculators by `AccountType`; uses `transactionDate` from `initialBalanceDate` through `asOfDate`; DEBIT/CASH/INVESTMENT return `currentBalance`; CREDIT_CARD returns `currentDebt`/`availableCredit` and `missingCreditDetails`. No persisted balances/UI/charts.                                                                                       |
| 2026-07-13 | FinancialAccount monetary scale ✅: `initialBalance` required, positive/zero/negative allowed, service rejects `scale > 2` without rounding on create/update/patch. 138 IT + 24 service. Superseded for balance status by the 2026-07-13 balance read model entry.                                                                                                                                                                                                 |
| 2026-07-12 | FinancialAccount domain rules ✅: final orchestrator delete implemented (TI tree → remaining FT → budget links → subscription account null → CAD → account), `initialBalanceDate` floor vs earliest transactionDate, `active=false` no side effects. 118 IT + 12 service. Superseded for balance status by the 2026-07-13 balance read model entry.                                                                                                                |
| 2026-07-10 | Added [`VALIDATIONS.md`](VALIDATIONS.md) — per-entity validation catalog across DTO, entity, DB, service, and REST layers.                                                                                                                                                                                                                                                                                                                                         |
| 2026-07-10 | **Validation hardening (Tag + FinancialAccount):** Tag `name` unique per owner (trim + case-insensitive); FA `currency`/`accountType` immutable; FA PATCH JsonNode; `400 invalid` mapping. 200 tests (69+12 Tag, 108+11 FA). Docs: TESTING, VALIDATIONS, IMPLEMENTATION.                                                                                                                                                                                           |
| 2026-07-10 | **Budget PATCH JsonNode:** M2M link semantics (`accounts`/`categories`/`tags` absent preserves, `null`/`[]` clears, ids replace). 122 tests (111+14 Budget).                                                                                                                                                                                                                                                                                                       |
| 2026-07-10 | **Category name uniqueness (hierarchical):** sibling-unique `name` per owner + `categoryType` + `parentCategory` (trim, case-insensitive); PATCH revalidates on `name`/`categoryType`/`parentCategory` change. 98 tests (84+14 Category).                                                                                                                                                                                                                          |
| 2026-07-10 | Added [`DOMAIN-RULES.md`](DOMAIN-RULES.md) — domain rules catalog (delete guards, balances, budget matching, rule motor, ingestion pipeline) with Fase 4/6 implementation order.                                                                                                                                                                                                                                                                                   |
| 2026-07-11 | [`DOMAIN-RULES.md`](DOMAIN-RULES.md) refocused: FA-only detail with Done/Proposed/Open per rule; other entities deferred.                                                                                                                                                                                                                                                                                                                                          |
| 2026-07-11 | FA DELETE spec: ordered domain cascade (budgets unlink, subscriptions null, credit details, transfers, ingestion tree, txs, account); UPDATE `initialBalanceDate` floor proposed.                                                                                                                                                                                                                                                                                  |
| 2026-07-11 | FA DELETE architecture plan: orchestration in `FinancialAccountService`; delegate `deleteAllForAccount` to `FinancialTransactionService` + `TransactionIngestionService`; single transaction. Superseded by 2026-07-12 implementation entry.                                                                                                                                                                                                                       |
| 2026-07-11 | [`DOMAIN-RULES.md`](DOMAIN-RULES.md) full plan: 17 entities in 3 groups (simple→complex); FA last; **next implement:** UserDashboardPreference.                                                                                                                                                                                                                                                                                                                    |
| 2026-07-11 | UserDashboardPreference domain rules: DELETE simple; `configuration` required + parseable JSON (`{}` OK); schema/`/me`/upsert deferred.                                                                                                                                                                                                                                                                                                                            |
| 2026-07-11 | ApiAccessTokenPermission domain rules ✅: confirmatory ITs (DELETE preserves token/sibling; CREATE on REVOKED/EXPIRED; same permission different token). Service unchanged. 37 IT.                                                                                                                                                                                                                                                                                 |
| 2026-07-11 | Category domain rules ✅: block delete with children; leaf delete+cleanup (FT/FS/budget/rule); parentCategory immutable; categoryType guard; child type matches parent. 103 IT + 16 service. Default categories on signup deferred.                                                                                                                                                                                                                                |
| 2026-07-11 | CreditAccountDetails domain rules ✅: direct DELETE blocked (`400` invalid; admin no bypass; foreign `404`); mutable credit fields without utilization checks; `CREDIT_CARD` details expected for full functionality but not enforced by `FinancialAccountService` today (atomic create / required-details enforcement deferred). 41 IT + 10 service.                                                                                                              |
| 2026-07-13 | CreditAccountDetails timestamp hardening ✅: `createdAt`/`updatedAt` server-owned; create ignores/missing client timestamps; PUT/PATCH reject explicit null/change and set `updatedAt=now` on success; frontend create no longer injects fake timestamps. 50 IT + 21 service + frontend CAD tests.                                                                                                                                                                 |
| 2026-07-13 | FinancialAccount/CreditAccountDetails UI composition ✅: `CREDIT_CARD` FinancialAccount create/edit embeds editable credit-card details without parent selector; detail embeds read-only section; standalone CAD CRUD remains; backend adds scoped `GET /api/credit-account-details/by-account/{accountId}` helper. 53 CAD IT + 22 CAD service + FA/CAD frontend tests.                                                                                            |
| 2026-07-17 | CSV Ingestion I1A/I1B backend ✅: canonical CSV parser/validator + workflow read model; persists `TransactionIngestion`, `FileIngestion`, and review `IngestionRecord`s only; no DB/JDL/Liquibase, no `FinancialTransaction` creation, no Rule Engine.                                                                                                                                                                                                             |
| 2026-07-17 | CSV Ingestion I1C frontend ✅: initial TransactionIngestion “New File Import” workflow; later consolidated into `/transaction-ingestion/new` parent-centered workflow.                                                                                                                                                                                                                                                                                             |
| 2026-07-17 | CSV Ingestion I2A status lifecycle ✅: `IngestionRecordStatus.CREATED` removed; review rows now use `VALID`; `IMPORTED` reserved for confirm import; data migration updates existing `CREATED` rows to `VALID`; no TransactionIngestion status change and no confirm import yet.                                                                                                                                                                                   |
| 2026-07-17 | CSV Ingestion I2B review flow ✅: upload page redirects to persisted TransactionIngestion review page; GET review endpoint returns FileIngestion metadata, counts and rows; enable/disable row actions implemented.                                                                                                                                                                                                                                                |
| 2026-07-18 | CSV Ingestion I2B.2 normalized row edit ✅: PATCH review-row endpoint + inline UI edit for normalized fields; `rawData.raw` preserved; `amount`/`flow` derived from `signedAmount`; edit revalidates `VALID`/`REJECTED`; `DISABLED` must be enabled before editing; confirm import, FinancialTransaction creation and Rule Engine remain deferred.                                                                                                                 |
| 2026-09-06 | CSV Ingestion TC-3B workflow candidate summaries ✅: `GET /api/transaction-ingestions/{id}/workflow` now exposes optional lightweight prepared `TransactionCandidate` summaries per row after TC-3A prepare; workflow GET remains read-only and does not create candidates, mutate `rawData`, create `FinancialTransaction` rows, or migrate Pantalla 2/Confirm Import.                                                                                            |
| 2026-09-08 | CSV Ingestion TC-3C.1 FILE candidate classification backend ✅: ingestion-scoped PATCH/preview/apply/confirm-no-suggestions endpoints classify prepared `FILE_IMPORT` `TransactionCandidate`s, use `TransactionOrigin.FILE_IMPORT` for rule evaluation, persist category/tags on candidates only, preserve `rawData`, create no `FinancialTransaction` rows, and leave Confirm Import backend internals unchanged.                                                 |
| 2026-09-08 | CSV Ingestion TC-3C.2 candidate-backed Pantalla 2 ✅: TransactionIngestion review UI prepares FILE candidates, reloads workflow candidate summaries, previews/applies/confirms suggestions through candidate endpoints, and persists category/tag decisions on candidates.                                                                                                                                                                                         |
| 2026-09-08 | CSV Ingestion TC-3D.2 frontend confirm cleanup ✅: current TransactionIngestion Pantalla 2 reloads and validates persisted candidates before confirm, then calls `POST /api/transaction-ingestions/{id}/confirm` with no legacy `records`/category/tag payload.                                                                                                                                                                                                    |
| 2026-09-08 | CSV Ingestion TC-3D.1 candidate-backed Confirm Import ✅: `POST /api/transaction-ingestions/{id}/confirm` now posts reviewed `FILE_IMPORT` candidates as the backend source of truth, links candidates and records to created `FinancialTransaction` rows, marks candidates `POSTED`, preserves `rawData`, completes all-or-nothing, and keeps completed retry idempotent.                                                                                         |
| 2026-07-18 | CSV Ingestion readiness status migration ✅: added `READY`/`PARTIALLY_READY` as pre-import review statuses; FILE review now produces readiness statuses, not `COMPLETED`/`PARTIALLY_COMPLETED`; Liquibase maps old FILE review `COMPLETED -> READY` and `PARTIALLY_COMPLETED -> PARTIALLY_READY`; `PARTIALLY_COMPLETED` remains reserved.                                                                                                                          |
| 2026-07-18 | TransactionIngestion create cleanup ✅: `/transaction-ingestion/new` is now the canonical FILE ingestion create workflow with Account + Ingestion Type + CSV file only; lifecycle/system fields hidden; FILE submit calls `POST /api/transaction-ingestions/file` and creates parent + file metadata + review rows together; API create remains TBD.                                                                                                               |
| 2026-09-10 | TC-4D generated technical route cleanup ✅: FileIngestion/IngestionRecord generated write routes and TransactionIngestion generated edit route now show a technical write-unavailable state; TransactionIngestion keeps one clear New File Import action; ApiIngestion TC-4C read-only behavior remains unchanged.                                                                                                                                                 |
| 2026-07-19 | TransactionIngestion detail cleanup ✅: `/transaction-ingestion/{id}` is now the canonical workflow detail/review route; it shows parent summary, embeds read-only FILE metadata, and renders IngestionRecord review/result rows through `GET /api/transaction-ingestions/{id}/workflow`; API detail is TBD; PENDING FILE without metadata shows an unavailable state.                                                                                             |
| 2026-07-18 | CSV Ingestion I2C confirm import ✅: initial `POST /api/transaction-ingestions/{id}/confirm` workflow recalculated readiness, imported `VALID` rows, marked rows `IMPORTED`, kept disabled rows skipped, marked parent `COMPLETED`, supported completed retry idempotently, and did not run Rule Engine. TC-3D.1 later migrated the backend source of truth from rawData/payload to reviewed `FILE_IMPORT` candidates.                                             |
| 2026-07-28 | TransactionCandidate TC-2A/TC-2A.1 backend manual commands ✅: `POST /api/transaction-candidates/manual`, `PATCH /api/transaction-candidates/{id}/manual-draft`, `POST /api/transaction-candidates/{id}/cancel`, and `POST /api/transaction-candidates/{id}/post`; manual post uses a pessimistic candidate lock, creates exactly one `FinancialTransaction` with `origin=MANUAL`, is idempotent/concurrency-safe, and does not invoke TransactionRule evaluation. |
| 2026-07-28 | TransactionCandidate TC-2B.1 manual autosave UI ✅: `/financial-transaction/new` creates no empty draft on load, creates a `MANUAL` candidate on first meaningful change, replaces the URL with `/financial-transaction/drafts/{id}`, debounces autosave, posts through the candidate post command, and keeps candidate-specific rule preview/apply deferred.                                                                                                      |
| 2026-07-28 | TransactionCandidate TC-2D.2 manual draft recovery page ✅: `/financial-transaction/drafts` loads `GET /api/transaction-candidates/manual-drafts`, shows current-user recoverable `MANUAL` `DRAFT`/`READY_TO_POST` summaries, links Resume to `/financial-transaction/drafts/{id}`, cancels through the candidate command, and keeps drafts separate from the posted FinancialTransaction table.                                                                   |
| 2026-07-11 | Grupo 1 delete confirmation dialogs ✅: domain-aware UX copy for UDP, AATP, Tag, Category; CAD informational-only (no confirm). i18n en/es.                                                                                                                                                                                                                                                                                                                        |
| 2026-07-11 | **Decision 11C — snapshot audit plan:** remove required `ApiIngestion`→`ApiAccessToken` FK; add snapshot fields; token DELETE allowed with historical ingestions; cascade permissions only. Superseded by implementation entry below.                                                                                                                                                                                                                              |
| 2026-07-11 | **Decision 11C implemented ✅:** snapshot fields + Liquibase `20260711160000`; token server-side generation + `rawToken` reveal modal; delete cascades permissions only; `SpaWebFilter` fix for `/api-*` frontend routes; ITs + service tests. Docs synced. Runtime API auth enforcement deferred fase 6.                                                                                                                                                          |

## Description normalization rules

Description normalization is implemented as a separate ingestion-focused rule system:

- entities:
  - `DescriptionNormalizationRule`;
  - `DescriptionNormalizationRuleCondition`.
- services:
  - `DescriptionNormalizationRuleService`;
  - `DescriptionNormalizationRuleConditionService`;
  - `DescriptionNormalizationRuleEvaluationService`.
- shared helpers:
  - `TextConditionMatcher`;
  - `ConditionGroupEvaluator`.

The evaluator loads active rules for the current owner by priority and returns the first matching `resultingDescription`. It does not save anything.

Architecture boundary:

- `DescriptionNormalizationRuleEvaluationService` uses `TextConditionMatcher` and `ConditionGroupEvaluator`.
- `TransactionRuleEvaluationService` intentionally keeps its existing matcher and ALL/ANY grouping logic for now. This avoids accidental behavior changes in category/tag `TransactionRule` evaluation.
- `TextConditionMatcher` is not yet a drop-in replacement for `TransactionRuleEvaluationService` because it uses `DescriptionNormalizationRuleOperator`, not `RuleOperator`; `TransactionRuleEvaluationService` supports `IN` / `NOT_IN`; invalid regex behavior differs; and case-insensitive regex flags differ.
- A future behavior-preserving refactor may introduce a shared lower-level text matcher or adapter once semantics are unified. `ConditionGroupEvaluator` may also be adopted later by `TransactionRuleEvaluationService`, but this is deferred because it is not needed for `DescriptionNormalizationRule` v1.

Integration points:

- `POST /api/transaction-ingestions/file`
- `POST /api/transaction-ingestions/{id}/file-ingestion`

Both FILE upload paths pass through `CsvIngestionWorkflowService.persistWorkflow`. After parser normalization and before `IngestionRecord` persistence, the service evaluates description normalization against `rawData.raw.description`. When matched, it updates `rawData.normalized.description` and writes `rawData.review.description`.

Workflow row DTOs expose `descriptionReview` as a read-only projection for the review UI. The projection reads the immutable original description from `rawData.raw.description`, the final importable description from `rawData.normalized.description`, and source/rule/edit metadata from `rawData.review.description`. It does not change persistence or the stored `rawData` shape.

Category/tag `TransactionRule` evaluation is not invoked during Confirm Import. By TC-3D.1, Confirm Import consumes reviewed `FILE_IMPORT` candidate fields, including the candidate description that was synced from `rawData.normalized.description` during candidate preparation. Pantalla 1 re-evaluation buttons and UserPreference-driven behavior remain deferred.
