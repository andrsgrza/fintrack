# FINTRACK CSV Ingestion v1 Design

## Purpose

CSV Ingestion v1 starts FINTRACK's file ingestion work using the existing ingestion entities:

- `TransactionIngestion` as the parent import process.
- `FileIngestion` as file metadata.
- `IngestionRecord` as the persisted review row.

The first implementation slice, I1, is backend persisted workflow only. It parses, validates, normalizes, and persists review rows, but it does not create `FinancialTransaction` rows and does not run the Rule Engine. I2C adds explicit confirm import for ready persisted workflows.

## 1. Current schema fit

I1 can be implemented without schema changes. This is an approved product/design decision for CSV workflow v1.

The current model has enough structure for a canonical CSV workflow:

- `TransactionIngestion` owns the import batch, target account, lifecycle status, and counters.
- `FileIngestion` stores file metadata, checksum, parser name/version, and statement date range.
- `IngestionRecord` stores one row per CSV data row, row status, row-level error summary, and `rawData` as a JSON payload.

Recommendation:

- Do not add normalized transaction columns in I1.
- Use `IngestionRecord.rawData` JSON as the I1 storage mechanism for original raw values, normalized review values, warnings, and validation errors.
- Keep `financialTransaction = null` for every `IngestionRecord` in I1.
- Leave `FinancialTransaction` creation to I2.

I2A removes the old `IngestionRecordStatus.CREATED` ambiguity. Valid review rows now use `VALID`; rows that generate a `FinancialTransaction` during confirm import use `IMPORTED`.

I2B adds a persistent TransactionIngestion review page. `/transaction-ingestion/new` is the canonical FILE ingestion creation workflow: it creates the parent `TransactionIngestion`, `FileIngestion` metadata, and review rows in one submit, then redirects to canonical workflow detail `/transaction-ingestion/{id}`, where the user can return later to inspect parent summary, read-only FileIngestion metadata, and review rows. TC-4D makes generated FileIngestion write routes unavailable in the UI; users do not manually create/edit/delete FileIngestion metadata from generated screens. I2B.1 supports enable/disable. I2B.2 supports editing normalized review-row values. I2C adds confirm import for `READY` reviews.

## 2. Responsibility split using current entities

### TransactionIngestion

Role: parent file import batch/process.

Recommended I1 values:

| Field             | Recommendation                                                                                             |
| ----------------- | ---------------------------------------------------------------------------------------------------------- |
| `ingestionType`   | `FILE`                                                                                                     |
| `status`          | `READY` when at least one row is `VALID` and no rows are `REJECTED`/`FAILED`; otherwise `PARTIALLY_READY`. |
| `sourceLabel`     | `Canonical CSV: <originalFilename>` trimmed to 100 characters.                                             |
| `startedAt`       | Server `now` at workflow creation start.                                                                   |
| `completedAt`     | Server `now` after parsing/persisting review rows completes.                                               |
| `recordsReceived` | Total CSV data rows read, excluding header.                                                                |
| `recordsCreated`  | `0` in I1 because no `FinancialTransaction` rows are created.                                              |
| `recordsSkipped`  | `0` in I1 unless duplicate/skipped-row semantics are added later.                                          |
| `recordsRejected` | Invalid row count.                                                                                         |
| `errorMessage`    | `null` for valid-header workflows; not used when the whole file is rejected before persistence.            |
| `createdAt`       | Server-owned `now`.                                                                                        |
| `account`         | Required target `FinancialAccount`, resolved with normal current-user ownership rules before save.         |

For invalid header, missing file, empty file, inaccessible account, oversized file, or unreadable file: reject the request and create nothing in I1. A persisted `FAILED` ingestion for rejected files can be reconsidered later if audit history for failed upload attempts becomes important.

### FileIngestion

Role: one-to-one child metadata for a file-based ingestion.

Recommended I1 values:

| Field                  | Recommendation                                                                                   |
| ---------------------- | ------------------------------------------------------------------------------------------------ |
| `originalFilename`     | Upload filename, normalized/truncated according to existing max length 255.                      |
| `fileType`             | `CSV`                                                                                            |
| `contentType`          | Upload content type, if provided, max 100.                                                       |
| `fileSizeBytes`        | Uploaded byte length.                                                                            |
| `checksum`             | SHA-256 hex digest of uploaded bytes. Current max length 128 is sufficient.                      |
| `storageKey`           | `null` in I1; file bytes are not stored unless an existing storage layer is intentionally added. |
| `parserName`           | `fintrack-canonical-csv`                                                                         |
| `parserVersion`        | `1.0`                                                                                            |
| `statementStartDate`   | Minimum `transactionDate` among valid rows; `null` if there are no valid rows.                   |
| `statementEndDate`     | Maximum `transactionDate` among valid rows; `null` if there are no valid rows.                   |
| `createdAt`            | Server-owned `now`.                                                                              |
| `transactionIngestion` | Required parent.                                                                                 |

Deriving statement dates from valid rows makes the metadata useful without requiring a separate manifest in v1. Invalid rows should not affect the statement range.

### IngestionRecord

Role: one persisted workflow row per CSV data row.

Recommended I1 values:

| Field                  | Recommendation                                                                                                                                             |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `recordIndex`          | 1-based CSV data-row index, excluding the header. The first data row is `1`.                                                                               |
| `externalRecordId`     | Normalized `externalReference` if present; otherwise `null`.                                                                                               |
| `status`               | `VALID` for valid review rows; `REJECTED` for invalid rows; `DISABLED`/`IMPORTED`/`SKIPPED_DUPLICATE`/`FAILED` reserved for later review/import lifecycle. |
| `rawData`              | JSON string containing `raw`, `normalized`, `errors`, and `warnings`.                                                                                      |
| `errorCode`            | First validation error code for rejected rows; `null` for valid rows.                                                                                      |
| `errorMessage`         | First validation error message for rejected rows; `null` for valid rows.                                                                                   |
| `createdAt`            | Server-owned `now`.                                                                                                                                        |
| `financialTransaction` | `null` in I1.                                                                                                                                              |
| `transactionIngestion` | Required parent.                                                                                                                                           |

### ApiIngestion

ApiIngestion remains API metadata/debug only in CSV/file-ingestion phases. The API ingestion runtime/product workflow is deferred.

Generated ApiIngestion list/detail pages may remain reachable for technical inspection, but ApiIngestion create/edit/delete are not product actions for the CSV workflow and should not be used as canonical ingestion commands. TC-4D applies the same frontend convention to FileIngestion and IngestionRecord: generated list/detail are read-only technical inspection surfaces, and direct generated write routes show an unavailable state instead of generated forms/modals.

## 3. `rawData` JSON contract

Recommendation: include raw values, normalized review values, all row-level errors, and warnings in `rawData`.

`errorCode` and `errorMessage` should duplicate the first error for simple querying and list display. `rawData.errors` remains the complete row error list.

Amounts should be stored as strings in JSON to avoid decimal precision surprises in JavaScript/JSON consumers.

Blank optional values normalize to `null`.

Example:

```json
{
  "raw": {
    "transactionDate": "2026-07-13",
    "postingDate": "",
    "description": " Uber trip ",
    "signedAmount": "-123.45",
    "currency": "MXN",
    "externalReference": "abc-123",
    "notes": ""
  },
  "normalized": {
    "transactionDate": "2026-07-13",
    "postingDate": null,
    "description": "Uber trip",
    "signedAmount": "-123.45",
    "amount": "123.45",
    "flow": "OUT",
    "currency": "MXN",
    "externalReference": "abc-123",
    "notes": null
  },
  "errors": [],
  "warnings": []
}
```

Rejected-row example:

```json
{
  "raw": {
    "transactionDate": "07/13/2026",
    "postingDate": "",
    "description": "",
    "signedAmount": "0",
    "currency": "MXN",
    "externalReference": "",
    "notes": ""
  },
  "normalized": {
    "transactionDate": null,
    "postingDate": null,
    "description": null,
    "signedAmount": "0",
    "amount": null,
    "flow": null,
    "currency": "MXN",
    "externalReference": null,
    "notes": null
  },
  "errors": [
    {
      "code": "INVALID_TRANSACTION_DATE",
      "message": "transactionDate must be ISO date YYYY-MM-DD"
    },
    {
      "code": "DESCRIPTION_REQUIRED",
      "message": "description is required"
    },
    {
      "code": "ZERO_SIGNED_AMOUNT",
      "message": "signedAmount must be nonzero"
    }
  ],
  "warnings": []
}
```

Recommended error-code style:

- `INVALID_HEADER`
- `MISSING_REQUIRED_COLUMN`
- `EMPTY_FILE`
- `ROW_LIMIT_EXCEEDED`
- `INVALID_TRANSACTION_DATE`
- `INVALID_POSTING_DATE`
- `DESCRIPTION_REQUIRED`
- `DESCRIPTION_TOO_LONG`
- `SIGNED_AMOUNT_REQUIRED`
- `INVALID_SIGNED_AMOUNT`
- `ZERO_SIGNED_AMOUNT`
- `AMOUNT_SCALE_EXCEEDED`
- `CURRENCY_REQUIRED`
- `UNSUPPORTED_CURRENCY`
- `CURRENCY_MISMATCH`
- `EXTERNAL_REFERENCE_TOO_LONG`
- `NOTES_TOO_LONG`

## 4. Endpoint design for I1

Use the canonical parent workflow endpoint for product creation:

```http
POST /api/transaction-ingestions/file
Content-Type: multipart/form-data
```

Input:

- `accountId`: target `FinancialAccount` id.
- `file`: uploaded canonical CSV file.

It creates the parent `TransactionIngestion`, file metadata, and persisted workflow rows in one workflow.

The canonical workflow detail endpoint is:

```http
GET /api/transaction-ingestions/{id}/workflow
```

It returns persisted parent status/counts, read-only file metadata, warnings, and review rows. It does not create `FinancialTransaction` rows.

For an already-created pending FILE parent, use the parent command endpoint:

```http
POST /api/transaction-ingestions/{id}/file-ingestion
Content-Type: multipart/form-data
```

Input:

- `file`: uploaded canonical CSV file.

This endpoint validates that the parent is owned by the current user, has `ingestionType = FILE`, has `status = PENDING`, has no existing `FileIngestion`, has no existing `IngestionRecord`s, and has no created `FinancialTransaction`s. It derives all `FileIngestion` metadata server-side, persists records, updates parent source/timestamps/counters/readiness, creates no `FinancialTransaction`s, and does not invoke the Rule Engine.

Response DTO:

```json
{
  "transactionIngestionId": 3501,
  "fileIngestionId": 3551,
  "status": "PARTIALLY_READY",
  "sourceLabel": "Canonical CSV: july.csv",
  "counts": {
    "recordsReceived": 10,
    "recordsCreated": 0,
    "recordsSkipped": 0,
    "recordsRejected": 2,
    "validRows": 8,
    "invalidRows": 2
  },
  "rows": [
    {
      "ingestionRecordId": 3601,
      "recordIndex": 1,
      "status": "VALID",
      "transactionDate": "2026-07-13",
      "postingDate": null,
      "description": "Uber trip",
      "signedAmount": "-123.45",
      "amount": "123.45",
      "flow": "OUT",
      "currency": "MXN",
      "externalReference": "abc-123",
      "notes": null,
      "errorCode": null,
      "errorMessage": null,
      "warnings": []
    }
  ]
}
```

For v1, return all rows in the response up to the accepted file limits:

- max file size: 2 MB.
- max data rows: 5,000.

These limits keep the response and persistence predictable for the first implementation. If real files exceed this, introduce paginated workflow retrieval before raising the limits.

Error behavior:

| Scenario                         | I1 behavior                                     | Persistence                 |
| -------------------------------- | ----------------------------------------------- | --------------------------- |
| Account inaccessible             | `404` or existing ownership error convention    | Create nothing              |
| Missing file                     | `400 error.invalid`                             | Create nothing              |
| Empty file                       | `400 error.invalid`                             | Create nothing              |
| Header-only file                 | `400 error.invalid`                             | Create nothing              |
| Invalid/missing/reordered header | `400 error.invalid`                             | Create nothing              |
| Extra columns                    | `400 error.invalid`                             | Create nothing              |
| File unreadable / malformed CSV  | `400 error.invalid`                             | Create nothing              |
| Oversized file / too many rows   | `400 error.invalid`                             | Create nothing              |
| Header valid, some rows invalid  | `200` with persisted workflow and rejected rows | Persist parent/file/records |
| Header valid, all rows invalid   | `200` with `PARTIALLY_READY` and rejected rows  | Persist parent/file/records |

## 5. Lifecycle/status mapping

Existing enum values:

```java
IngestionType: FILE, API
IngestionStatus: PENDING, READY, PARTIALLY_READY, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED
ImportFileType: CSV, PDF, XLSX, JSON, OFX, OTHER
IngestionRecordStatus: VALID, DISABLED, IMPORTED, SKIPPED_DUPLICATE, REJECTED, FAILED
```

Recommended I1 mapping:

| Case                                       | TransactionIngestion.status           | IngestionRecord.status           |
| ------------------------------------------ | ------------------------------------- | -------------------------------- |
| Valid header, all rows valid               | `READY`                               | `VALID`                          |
| Valid header, valid + disabled only        | `READY`                               | `VALID` / `DISABLED`             |
| Valid header, some rows invalid            | `PARTIALLY_READY`                     | `VALID` / `REJECTED`             |
| Valid header, all rows invalid             | `PARTIALLY_READY`                     | `REJECTED`                       |
| Valid header, zero valid rows after review | `PARTIALLY_READY`                     | `DISABLED` / `SKIPPED_DUPLICATE` |
| Invalid header / rejected file             | No persisted ingestion in I1          | N/A                              |
| Preview ready but not imported             | `READY` or `PARTIALLY_READY` as above | `VALID` rows remain unlinked     |

I2A migrated away from `CREATED`. I2 readiness migration introduced `READY`/`PARTIALLY_READY` for pre-import review. `READY` requires at least one `VALID` row and no `REJECTED`/`FAILED` rows. `PARTIALLY_READY` means blocking rows exist or there are zero `VALID` rows to import. `COMPLETED` and `PARTIALLY_COMPLETED` are import-result statuses; `PARTIALLY_COMPLETED` is reserved for future/exceptional partial import scenarios and should not be used by CSV Confirm Import v1. `IMPORTED` is used for rows that generated a `FinancialTransaction` during confirm import. `DISABLED` is used by review actions where the user keeps a row for audit but excludes it from import. `DISABLED` and `SKIPPED_DUPLICATE` rows do not block readiness by themselves, but an ingestion with only disabled/skipped rows is `PARTIALLY_READY` because there is nothing importable. `REJECTED`/`FAILED` rows make the batch `PARTIALLY_READY` unless disabled/fixed.

I2B.2 row edits replace `rawData.normalized`, recalculate `rawData.errors`/`rawData.warnings`, and leave `rawData.raw` unchanged as the original CSV audit payload. Editable normalized fields are `transactionDate`, `postingDate`, `description`, `signedAmount`, `currency`, `externalReference`, and `notes`. `amount` and `flow` are always derived from `signedAmount`; clients cannot edit status, amount, flow, parent ingestion, record index, raw CSV data, or financial transaction links. Editing is allowed only for `VALID` and `REJECTED` rows; valid results become `VALID`, invalid results become `REJECTED`. `DISABLED` rows must be enabled before editing. `IMPORTED`, `SKIPPED_DUPLICATE`, and `FAILED` rows are immutable in this slice.

Counter mapping:

| Counter           | I1 value                                                             |
| ----------------- | -------------------------------------------------------------------- |
| `recordsReceived` | Total data rows read, excluding header.                              |
| `recordsCreated`  | `0`, because I1 creates no financial transactions.                   |
| `recordsSkipped`  | `0`, because duplicate skip handling is deferred.                    |
| `recordsRejected` | Count of invalid/rejected rows.                                      |
| `validRows`       | Response-only: `recordsReceived - recordsRejected - recordsSkipped`. |
| `invalidRows`     | Response-only: `recordsRejected`.                                    |

## 6. CSV validation rules

### Header

Canonical v1 header must be exact, ordered, and case-sensitive:

```csv
transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
```

Missing, extra, reordered, or case-changed columns reject the whole file and create nothing. This keeps v1 deterministic and avoids accidental mapper semantics.

CSV format:

- UTF-8.
- RFC4180-style quoted fields supported by the CSV library.
- Quoted commas and quoted line breaks should be accepted if the chosen library supports them safely.

### File

Recommended limits:

- max file size: 2 MB.
- max data rows: 5,000.

File validation:

- missing file: reject.
- empty file: reject.
- header-only file: reject.
- unreadable/malformed CSV: reject.

### Rows

Row validation:

| Field               | Rule                                                                                                     |
| ------------------- | -------------------------------------------------------------------------------------------------------- |
| `transactionDate`   | Required ISO date `YYYY-MM-DD`.                                                                          |
| `postingDate`       | Optional ISO date `YYYY-MM-DD`; blank normalizes to `null`.                                              |
| `description`       | Required nonblank after trim; max 500 after trim.                                                        |
| `signedAmount`      | Required decimal; must be nonzero.                                                                       |
| `amount`            | `abs(signedAmount)`; must be compatible with `FinancialTransaction.amount` precision/scale, max scale 2. |
| `flow`              | `IN` when `signedAmount > 0`; `OUT` when `signedAmount < 0`.                                             |
| `currency`          | Required; must be one of current `CurrencyCode` values (`MXN`, `USD`, `EUR`).                            |
| Account currency    | Row `currency` must equal selected `FinancialAccount.currency`.                                          |
| `externalReference` | Optional; trim; blank to `null`; max 150 after trim.                                                     |
| `notes`             | Optional; trim; blank to `null`; max 1000 after trim.                                                    |

Zero `signedAmount` is invalid in v1. It should not be silently skipped because the canonical contract says every row represents a real movement.

Duplicate detection is deferred in I1 except for checksum warning at file level.

## 7. Account and ownership

I1 must use normal-user ownership rules:

- Current user must own the target account.
- Admin has no special import bypass.
- Admin cannot import into another user's account.
- Account is resolved before any ingestion rows are created.
- Account currency must match every row currency.
- Account type does not affect sign semantics; canonical CSV sign rules are account-type independent.
- `FileIngestion` derives account through `TransactionIngestion`.

## 8. Duplicate and idempotency policy

I1 recommendation:

- Compute SHA-256 checksum and store it in `FileIngestion.checksum`.
- If the same checksum was previously ingested for the same account, return a warning in the response and optionally in row/global workflow metadata.
- Do not block upload/review solely on checksum in I1.
- Do not reject row-level duplicates based on weak heuristics in I1.
- Defer existing-transaction duplicate detection to I2/I3.

Rationale: a repeated upload warning is useful, but strict blocking requires a clear idempotency policy and user recovery path.

## 9. Rule Engine interaction

I1:

- Rule Engine does not run.
- No `FinancialTransaction` rows are created.
- Preview rows do not include category/tag suggestions.
- I1 focuses only on parsing, validation, normalization, and persisted `IngestionRecord` rows.

I2C:

- Confirm import creates `FinancialTransaction` rows from valid `IngestionRecord` rows.
- Imported transactions should use `origin = FILE_IMPORT`.
- TC-3D.1 migrates Confirm Import backend internals to use prepared `FILE_IMPORT` `TransactionCandidate`s as the source of final transaction fields and category/tag classification.
- In the active candidate lifecycle, `FILE_IMPORT` candidates are prepared for `VALID` rows and normally move from `READY_TO_POST` to `POSTED` during Confirm Import. There is no global `NEEDS_REVIEW` candidate status; review needs are represented by `validationStatus`, `descriptionReviewStatus`, and `classificationReviewStatus`. `API_IMPORT` remains reserved/deferred and is not part of CSV v1 runtime behavior.
- Confirm import does not run the Rule Engine itself.
- Slice 2A added the old backend category/tag review support: `POST /api/transaction-ingestions/{id}/classification-preview`. TC-4B removes that endpoint/service/DTO path because there is no active consumer; active Pantalla 2 product behavior uses candidate-backed preview/apply endpoints.
- Slice 2B originally added Pantalla 2 in the TransactionIngestion workflow UI for reviewing category/tag suggestions before confirm.
- TC-3C.2 migrates Pantalla 2 to candidate-backed classification: the UI prepares/syncs `FILE_IMPORT` `TransactionCandidate`s, reloads the workflow, previews rules through candidate endpoints, and persists category/tag review choices on candidates.
- TC-3D.2 removes the current frontend legacy confirm payload adapter. TC-4B removes backend parsing/validation of the old confirm `records/categoryId/tagIds` body; Confirm Import reads persisted candidates as the source of truth.
- Candidate category/tags are validated defensively for current-user ownership and category flow compatibility.
- Category/tag suggestions also follow the category/tag TransactionRule evaluator semantics. A rule that targets an EXPENSE category should include an effective `FLOW = OUT` condition, and a rule that targets an INCOME category should include an effective `FLOW = IN` condition through the configured TransactionRule guard. For example, an Uber expense rule should suggest the expense category for an OUT row and not for an IN/refund row.
- Confirm import does not persist category/tag selections or evaluation results back into `rawData`.
- `FinancialSubscription` remains empty in CSV v1 confirm import.
- Pantalla 2 selections now persist on `TransactionCandidate`; browser refresh reloads reviewed category/tags from workflow row candidate summaries.
- Fase 3-B hardens QA without changing product behavior. Cypress now covers the real TransactionIngestion workflow for invalid-header upload failure, `PARTIALLY_READY` rejected-row blocking before Pantalla 2, completed read-only/reload behavior, and disabling one valid row before category/tag review so only enabled valid rows import. The old generated `transaction-ingestion.cy.ts` is kept as a workflow smoke spec, while `file-ingestion.cy.ts` and `ingestion-record.cy.ts` are technical/read-only smoke specs.
- TC-3A adds backend-only `POST /api/transaction-ingestions/{id}/candidates/prepare`. It creates or syncs `FILE_IMPORT` `TransactionCandidate` rows for `VALID` `IngestionRecord`s after Pantalla 1. The command is idempotent, skips non-`VALID` rows, preserves existing candidate category/tags, marks fresh classification review `STALE` when rule-input fields change, does not mutate `rawData`, and does not create `FinancialTransaction` rows.
- TC-5D.1 tightens Pantalla 1 row review after candidates have been prepared. Disabling a pre-confirm row removes its unposted `FILE_IMPORT` candidate and candidate tag joins. Re-enabling revalidates the row but does not restore the old candidate; the next prepare creates a fresh candidate if the row is `VALID`. Editing a prepared row syncs an existing unposted candidate immediately when the row remains `VALID`, preserving category/tags and marking fresh classification `STALE` when rule-input fields changed; editing the row to non-`VALID` removes the unposted candidate. Posted/imported candidates are not silently deleted.
- TC-3B extends `GET /api/transaction-ingestions/{id}/workflow` with an optional lightweight prepared candidate summary per row. The read model is strictly read-only: it does not create/sync candidates, mutate `rawData`, create `FinancialTransaction` rows, or migrate Confirm Import/Pantalla 2 behavior.
- TC-3C.1 adds ingestion-scoped FILE_IMPORT candidate classification commands:
  - `PATCH /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/classification`;
  - `POST /api/transaction-ingestions/{ingestionId}/candidates/rule-preview`;
  - `POST /api/transaction-ingestions/{ingestionId}/candidates/apply-rules`;
  - `POST /api/transaction-ingestions/{ingestionId}/candidates/{candidateId}/confirm-no-suggestions`.
- These candidate endpoints classify prepared `FILE_IMPORT` candidates only. They do not mutate `IngestionRecord.rawData` and do not create `FinancialTransaction` rows before Confirm Import.
- Candidate rule preview/apply evaluates active owner TransactionRules with `TransactionOrigin.FILE_IMPORT`. Apply uses `FILL_EMPTY_ONLY`: empty category can be filled, existing category is preserved, and tags are additive/deduplicated.
- Candidate classification PATCH stores category/tags on `TransactionCandidate`, not in `rawData`. It must include at least one of `categoryId` or `tagIds`; omitted fields preserve existing values. Category compatibility still follows row flow: OUT accepts EXPENSE/BOTH, IN accepts INCOME/BOTH.
- TC-3D.1 Confirm Import requires every current `VALID` row to have exactly one reviewed, valid, `READY_TO_POST` `FILE_IMPORT` candidate. Confirm creates `FinancialTransaction`s from candidates, marks candidates `POSTED`, links candidates and `IngestionRecord`s to the created transactions, marks rows `IMPORTED`, and marks the parent ingestion `COMPLETED` all-or-nothing.

Future:

- Bulk reevaluation remains deferred.

## 10. UI design

I1C UI starts from `TransactionIngestion`, not generated `FileIngestion` create.

TransactionIngestion page/list:

- Add "New File Import" button. Implemented at `/transaction-ingestion/new`.
- User selects account.
- User selects ingestion type; `FILE` shows CSV upload and `API` shows a TBD placeholder.
- User uploads canonical CSV.
- Submit upload.
- Redirect to `/transaction-ingestion/{id}`.
- Show persisted review result, read-only file metadata, counts, rows, and enable/disable review actions.

Workflow review screen:

- Summary counts.
- Rows table:
  - `transactionDate`
  - `postingDate`
  - `description`
  - `signedAmount`
  - `amount`
  - `flow`
  - `currency`
  - `externalReference`
  - `status`
  - `error`
- Expandable raw row if useful.
- No `FinancialTransaction` rows are created in I1.
- Confirm Import appears only on the persisted review page when the recalculated parent status is `READY` and at least one `VALID` row exists.

Future shortcut:

- `FinancialAccount` detail can add "Import transactions".
- Same flow.
- Account preselected.

FileIngestion:

- Not the main create UX.
- Generated list/detail remain available for technical/debug inspection.
- List/detail show technical/read-only context markers.
- List/detail hide Create/Edit/Delete because file metadata is server-derived and parent-owned.
- Generated write routes (`/file-ingestion/new`, `/file-ingestion/{id}/edit`, `/file-ingestion/{id}/delete`) show the technical write-unavailable state. Users upload files through `/transaction-ingestion/new`.

IngestionRecord:

- Not the main create/edit UX.
- Generated list/detail remain available for technical/debug inspection.
- List/detail show technical/read-only context markers.
- Generated write routes (`/ingestion-record/new`, `/ingestion-record/{id}/edit`, `/ingestion-record/{id}/delete`) show the technical write-unavailable state. Pantalla 1 remains the canonical row review/edit workflow.
- List/detail hide Create/Edit/Delete because review-row actions are managed from the TransactionIngestion workflow review page.

ApiIngestion:

- Untouched.

UI composition conventions:

- Do not embed full `FileIngestion` CRUD inside `TransactionIngestion`.
- Use contextual upload/review components, not generated child CRUD.
- For high-volume records, use a related list or paginated review table, not an inline editable collection.
- Preserve generated CRUD pages as fallback/debug where useful, with clear Technical marking when they are not the canonical workflow.

Temporary generated ingestion write surfaces:

- `/transaction-ingestion/:id/edit`, `POST /api/transaction-ingestions`, `PUT /api/transaction-ingestions/{id}`, and `PATCH /api/transaction-ingestions/{id}` remain generated/technical write surfaces.
- Generated FileIngestion write routes and `POST`/`PUT`/`PATCH /api/file-ingestions` remain generated/technical write surfaces.
- Generated IngestionRecord write routes and `POST`/`PUT`/`PATCH /api/ingestion-records` remain generated/technical write surfaces.
- These are not canonical CSV product workflow paths.
- Canonical product writes are the TransactionIngestion workflow command endpoints.
- Generic reducer thunks and ResourceIT coverage may remain until the generated technical routes are removed.
- A later backend hardening slice may reject or remove the generated write paths.

## 11. Proposed implementation phases

### I1A — Parser/validator service

- Implement canonical CSV parser.
- Validate header, file limits, and row fields.
- Produce normalized row results in memory.
- Unit tests only.
- No persistence yet.

### I1B — Persisted workflow endpoint

- Add `POST /api/transaction-ingestions/file` for canonical parent workflow creation.
- Add `GET /api/transaction-ingestions/{id}/workflow` for canonical workflow detail/review data.
- Resolve account ownership first.
- Create `TransactionIngestion`.
- Create `FileIngestion`.
- Create `IngestionRecord` rows.
- Return workflow DTO.
- Integration/resource tests.
- No `FinancialTransaction` creation.
- No Rule Engine.

### I1C — Minimal UI

- Add TransactionIngestion upload/review flow. **Implemented.**
- Show summary and row table. **Implemented.**
- No confirm/import action yet. **Implemented.**

### I2 — Confirm import

- Add `POST /api/transaction-ingestions/{id}/confirm`. **Implemented.**
- Recalculate readiness from persisted records before import. **Implemented.**
- Require `READY`: at least one `VALID` row and zero `REJECTED`/`FAILED` rows. **Implemented.**
- Create `FinancialTransaction` rows from `VALID` `IngestionRecord` rows. **Implemented.**
- Use `origin = FILE_IMPORT`. **Implemented.**
- Set imported row status to `IMPORTED` and link each row to its created `FinancialTransaction`. **Implemented.**
- Leave `DISABLED` rows skipped/read-only. **Implemented.**
- Mark parent `TransactionIngestion` `COMPLETED` after a successful all-or-nothing import. **Implemented.**
- Do not produce `PARTIALLY_COMPLETED` in CSV v1. **Implemented.**
- Do not run the Rule Engine during CSV v1 confirm import. **Implemented.**

### I3 — Duplicate/idempotency improvements

- Define strict file-level and row-level idempotency.
- Detect duplicate existing transactions if product wants it.
- Decide whether repeated checksum blocks or only warns.

### I4 — FinancialAccount shortcut

- Add "Import transactions" from account detail.
- Reuse same backend endpoint and workflow UI.
- Preselect account.

### I5 — CSV mapper, if needed

- Optional column mapper for non-canonical CSV files.
- Not part of v1.

## 12. Tests to plan

### Backend unit tests for I1A

- exact header accepted.
- missing required column rejected.
- extra header rejected.
- reordered header rejected.
- case-changed header rejected.
- invalid `transactionDate`.
- invalid `postingDate`.
- blank `description`.
- description max length.
- zero `signedAmount`.
- malformed `signedAmount`.
- signed amount scale over 2 rejected.
- positive `signedAmount` normalizes to `flow = IN` and absolute amount.
- negative `signedAmount` normalizes to `flow = OUT` and absolute amount.
- unsupported currency.
- account currency mismatch.
- `externalReference` trim and blank-to-null.
- `notes` trim and blank-to-null.
- quoted CSV field with comma.
- empty file.
- header-only file.
- row count limit.
- file size limit.

### Backend integration/resource tests for I1B

- valid CSV upload creates `TransactionIngestion`, `FileIngestion`, and `IngestionRecord` rows.
- invalid rows persist as `REJECTED` records.
- invalid header creates nothing.
- inaccessible account rejected.
- admin foreign account rejected.
- checksum stored.
- parser name/version stored as `fintrack-canonical-csv` / `1.0`.
- statement start/end dates derive from valid rows.
- row counts correct.
- no `FinancialTransaction` rows created.
- Rule Engine not invoked.
- duplicate checksum produces warning but does not block.

### I1C frontend tests

- account required.
- file required.
- multipart workflow submit.
- workflow summary renders counts.
- duplicate checksum warning renders.
- invalid row errors render.
- no confirm/import action in I1.
- future account shortcut preselects account remains deferred.

## 13. Docs to update during implementation later

Implementation phases should update:

- `docs/DOMAIN-RULES.md`
- `docs/VALIDATIONS.md`
- `docs/IMPLEMENTATION.md`
- `docs/TESTING.md`
- `docs/UI-COMPOSITION.md`
- possibly `docs/CSV-INGESTION.md`

For this design phase, only this file is added.

## 14. Closed I1 product decisions

No open product questions remain for I1.

Closed decisions:

1. Repeated checksum for the same account is warning-only in I1; it does not block upload/review.
2. File limits are 2 MB and 5,000 data rows.
3. Invalid/rejected uploads create nothing in I1. This includes invalid header, missing file, empty file, header-only file, unreadable file, and oversized file. Persisted `FAILED` ingestion rows for rejected uploads remain deferred.

Already decided and not open for I1:

- canonical CSV only.
- no mapper v1.
- no PDF parsing inside FINTRACK.
- parser name `fintrack-canonical-csv`.
- parser version `1.0`.
- no API ingestion changes.
- `FileIngestion` is not user-created as the main flow.
- account comes through `TransactionIngestion`.
- I1 does not run Rule Engine.
- I1 does not create `FinancialTransaction` rows.
- I2C confirm import does not run the Rule Engine. Slice 2A added a separate read-only `classification-preview` endpoint for the old import-time category/tag suggestion flow; TC-4B removes that old endpoint. Active Pantalla 2 uses candidate-backed preview/apply endpoints.

## Description normalization during upload

FILE ingestion upload now has an optional pre-review description normalization step.

- The parser still creates `rawData.raw` and `rawData.normalized`.
- Description normalization evaluates `rawData.raw.description` only.
- If a rule matches, `rawData.normalized.description` is replaced with the rule's `resultingDescription`.
- `rawData.raw` remains immutable.
- `rawData.review.description` stores metadata about rule/user modifications.
- `rawData.review.description` does not duplicate `originalDescription`; the original remains only in `rawData.raw.description`.
- `rawData.suggestions` is not used.
- UserPreference-based behavior is deferred.
- Workflow row responses expose a read-only `descriptionReview` projection for UI display. It is derived from `rawData.raw.description`, `rawData.normalized.description`, and `rawData.review.description`; it does not change persisted `rawData`.

Rule-applied metadata shape:

```json
{
  "description": {
    "source": "DESCRIPTION_RULE",
    "ruleId": 42,
    "ruleName": "Normalize Uber",
    "resultingDescription": "Uber",
    "editedAt": null,
    "editedBy": null
  }
}
```

User-edit metadata shape:

```json
{
  "description": {
    "source": "USER_EDIT",
    "ruleId": 42,
    "ruleName": "Normalize Uber",
    "resultingDescription": "Uber",
    "editedAt": "...",
    "editedBy": "..."
  }
}
```

No FinancialTransactions are created during upload/review. TC-3A candidate preparation also creates no FinancialTransactions and leaves `rawData` unchanged. TC-3B exposes prepared candidates in the workflow response but keeps workflow GET read-only. TC-3C.2 uses candidate-backed Pantalla 2 classification commands that persist category/tags on prepared `FILE_IMPORT` candidates, never in `rawData`. TC-3D.1 Confirm Import posts those candidates into final `FinancialTransaction` rows. TC-3D.2 current frontend confirm validates persisted candidates, then posts `/confirm` with no legacy `records`/category/tag payload; backend category/tags come from persisted candidates. TC-4B removes the old `classification-preview` implementation and backend parsing of the old confirm `records/categoryId/tagIds` body. UserPreference-driven rule behavior is deferred.
