# FINTRACK CSV Ingestion v1 Design

## Purpose

CSV Ingestion v1 starts FINTRACK's file ingestion work using the existing ingestion entities:

- `TransactionIngestion` as the parent import process.
- `FileIngestion` as file metadata.
- `IngestionRecord` as the persisted row preview.

The first implementation slice, I1, is backend persisted preview only. It parses, validates, normalizes, and persists preview rows, but it does not create `FinancialTransaction` rows and does not run the Rule Engine.

## 1. Current schema fit

I1 can be implemented without schema changes. This is an approved product/design decision for CSV preview v1.

The current model has enough structure for a canonical CSV preview:

- `TransactionIngestion` owns the import batch, target account, lifecycle status, and counters.
- `FileIngestion` stores file metadata, checksum, parser name/version, and statement date range.
- `IngestionRecord` stores one row per CSV data row, row status, row-level error summary, and `rawData` as a JSON payload.

Recommendation:

- Do not add normalized transaction columns in I1.
- Use `IngestionRecord.rawData` JSON as the I1 storage mechanism for original raw values, normalized preview values, warnings, and validation errors.
- Keep `financialTransaction = null` for every `IngestionRecord` in I1.
- Leave `FinancialTransaction` creation to I2.

I2A removes the old `IngestionRecordStatus.CREATED` ambiguity. Valid preview rows now use `VALID`; rows that already generated a `FinancialTransaction` will use `IMPORTED` in a later confirm-import slice.

I2B adds a persistent TransactionIngestion review page. `/transaction-ingestion/file-preview/new` creates the persisted preview and redirects to `/transaction-ingestion/{id}/file-preview`, where the user can return later to inspect read-only FileIngestion metadata and enable/disable preview rows. Confirm import remains deferred.

## 2. Responsibility split using current entities

### TransactionIngestion

Role: parent file import batch/process.

Recommended I1 values:

| Field             | Recommendation                                                                                     |
| ----------------- | -------------------------------------------------------------------------------------------------- |
| `ingestionType`   | `FILE`                                                                                             |
| `status`          | `COMPLETED` if all rows are valid; `PARTIALLY_COMPLETED` if header is valid but some rows reject.  |
| `sourceLabel`     | `Canonical CSV: <originalFilename>` trimmed to 100 characters.                                     |
| `startedAt`       | Server `now` at preview creation start.                                                            |
| `completedAt`     | Server `now` after parsing/persisting preview rows completes.                                      |
| `recordsReceived` | Total CSV data rows read, excluding header.                                                        |
| `recordsCreated`  | `0` in I1 because no `FinancialTransaction` rows are created.                                      |
| `recordsSkipped`  | `0` in I1 unless duplicate/skipped-row semantics are added later.                                  |
| `recordsRejected` | Invalid row count.                                                                                 |
| `errorMessage`    | `null` for valid-header previews; not used when the whole file is rejected before persistence.     |
| `createdAt`       | Server-owned `now`.                                                                                |
| `account`         | Required target `FinancialAccount`, resolved with normal current-user ownership rules before save. |

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

Role: one persisted preview row per CSV data row.

Recommended I1 values:

| Field                  | Recommendation                                                                                                                                              |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `recordIndex`          | 1-based CSV data-row index, excluding the header. The first data row is `1`.                                                                                |
| `externalRecordId`     | Normalized `externalReference` if present; otherwise `null`.                                                                                                |
| `status`               | `VALID` for valid preview rows; `REJECTED` for invalid rows; `DISABLED`/`IMPORTED`/`SKIPPED_DUPLICATE`/`FAILED` reserved for later review/import lifecycle. |
| `rawData`              | JSON string containing `raw`, `normalized`, `errors`, and `warnings`.                                                                                       |
| `errorCode`            | First validation error code for rejected rows; `null` for valid rows.                                                                                       |
| `errorMessage`         | First validation error message for rejected rows; `null` for valid rows.                                                                                    |
| `createdAt`            | Server-owned `now`.                                                                                                                                         |
| `financialTransaction` | `null` in I1.                                                                                                                                               |
| `transactionIngestion` | Required parent.                                                                                                                                            |

### ApiIngestion

Untouched in this phase.

## 3. `rawData` JSON contract

Recommendation: include raw values, normalized preview values, all row-level errors, and warnings in `rawData`.

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

Use one endpoint:

```http
POST /api/transaction-ingestions/file-preview
Content-Type: multipart/form-data
```

Input:

- `accountId`: target `FinancialAccount` id.
- `file`: uploaded canonical CSV file.

Response DTO:

```json
{
  "transactionIngestionId": 3501,
  "fileIngestionId": 3551,
  "status": "PARTIALLY_COMPLETED",
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

These limits keep the response and persistence predictable for the first implementation. If real files exceed this, introduce paginated preview retrieval before raising the limits.

Error behavior:

| Scenario                         | I1 behavior                                        | Persistence                 |
| -------------------------------- | -------------------------------------------------- | --------------------------- |
| Account inaccessible             | `404` or existing ownership error convention       | Create nothing              |
| Missing file                     | `400 error.invalid`                                | Create nothing              |
| Empty file                       | `400 error.invalid`                                | Create nothing              |
| Header-only file                 | `400 error.invalid`                                | Create nothing              |
| Invalid/missing/reordered header | `400 error.invalid`                                | Create nothing              |
| Extra columns                    | `400 error.invalid`                                | Create nothing              |
| File unreadable / malformed CSV  | `400 error.invalid`                                | Create nothing              |
| Oversized file / too many rows   | `400 error.invalid`                                | Create nothing              |
| Header valid, some rows invalid  | `200` with persisted preview and rejected rows     | Persist parent/file/records |
| Header valid, all rows invalid   | `200` with `PARTIALLY_COMPLETED` and rejected rows | Persist parent/file/records |

## 5. Lifecycle/status mapping

Existing enum values:

```java
IngestionType: FILE, API
IngestionStatus: PENDING, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED
ImportFileType: CSV, PDF, XLSX, JSON, OFX, OTHER
IngestionRecordStatus: VALID, DISABLED, IMPORTED, SKIPPED_DUPLICATE, REJECTED, FAILED
```

Recommended I1 mapping:

| Case                               | TransactionIngestion.status                   | IngestionRecord.status       |
| ---------------------------------- | --------------------------------------------- | ---------------------------- |
| Valid header, all rows valid       | `COMPLETED`                                   | `VALID`                      |
| Valid header, some rows invalid    | `PARTIALLY_COMPLETED`                         | `VALID` / `REJECTED`         |
| Valid header, all rows invalid     | `PARTIALLY_COMPLETED`                         | `REJECTED`                   |
| Invalid header / rejected file     | No persisted ingestion in I1                  | N/A                          |
| Completed preview but not imported | `COMPLETED` or `PARTIALLY_COMPLETED` as above | `VALID` rows remain unlinked |

I2A migrated away from `CREATED`. `IMPORTED` is reserved for rows that generate a `FinancialTransaction` during a later confirm-import slice. `DISABLED` is reserved for review actions where the user keeps a row for audit but excludes it from import. `REJECTED` rows block future confirm import unless fixed or disabled. TransactionIngestion status is unchanged in I2A.

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
- Admin cannot preview/import into another user's account.
- Account is resolved before any ingestion rows are created.
- Account currency must match every row currency.
- Account type does not affect sign semantics; canonical CSV sign rules are account-type independent.
- `FileIngestion` derives account through `TransactionIngestion`.

## 8. Duplicate and idempotency policy

I1 recommendation:

- Compute SHA-256 checksum and store it in `FileIngestion.checksum`.
- If the same checksum was previously ingested for the same account, return a warning in the response and optionally in row/global preview metadata.
- Do not block preview solely on checksum in I1.
- Do not reject row-level duplicates based on weak heuristics in I1.
- Defer existing-transaction duplicate detection to I2/I3.

Rationale: a repeated upload warning is useful, but strict blocking requires a clear idempotency policy and user recovery path.

## 9. Rule Engine interaction

I1:

- Rule Engine does not run.
- No `FinancialTransaction` rows are created.
- Preview rows do not include category/tag suggestions.
- I1 focuses only on parsing, validation, normalization, and persisted `IngestionRecord` rows.

I2:

- Confirm import creates `FinancialTransaction` rows from valid `IngestionRecord` rows.
- Imported transactions should use `origin = FILE_IMPORT`.
- Rule Engine `FILL_EMPTY_ONLY` should be explicitly applied during confirm import unless a clear design issue is found.
- Rule Engine use must be explicit and tested, not an accidental side effect of calling a generic save path.
- Category/tags from rules fill empty values only.
- No import UI rule suggestions in I2 unless separately designed.

Future:

- Optional per-row category/tag suggestion preview.
- Optional bulk review before import.
- Bulk reevaluation remains deferred.

## 10. UI design

I1C UI starts from `TransactionIngestion`, not generated `FileIngestion` create.

TransactionIngestion page/list:

- Add "New File Import" button. Implemented at `/transaction-ingestion/file-preview/new`.
- User selects account.
- User uploads canonical CSV.
- Submit preview.
- Redirect to `/transaction-ingestion/{id}/file-preview`.
- Show persisted review result, read-only file metadata, counts, rows, and enable/disable review actions.

Preview screen:

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
- Confirm/import button is absent until I2.

Future shortcut:

- `FinancialAccount` detail can add "Import transactions".
- Same flow.
- Account preselected.

FileIngestion:

- Not the main create UX.
- Generated create/edit does not need polish for I1.
- Later list/detail can remain debug/admin-ish metadata views if useful.

ApiIngestion:

- Untouched.

UI composition conventions:

- Do not embed full `FileIngestion` CRUD inside `TransactionIngestion`.
- Use contextual upload/preview components, not generated child CRUD.
- For high-volume records, use a related list or paginated preview table, not an inline editable collection.
- Preserve generated CRUD pages as fallback/debug where useful.

## 11. Proposed implementation phases

### I1A — Parser/validator service

- Implement canonical CSV parser.
- Validate header, file limits, and row fields.
- Produce normalized row results in memory.
- Unit tests only.
- No persistence yet.

### I1B — Persisted preview endpoint

- Add `POST /api/transaction-ingestions/file-preview`.
- Resolve account ownership first.
- Create `TransactionIngestion`.
- Create `FileIngestion`.
- Create `IngestionRecord` rows.
- Return preview DTO.
- Integration/resource tests.
- No `FinancialTransaction` creation.
- No Rule Engine.

### I1C — Minimal UI

- Add TransactionIngestion upload/preview flow. **Implemented.**
- Show summary and row table. **Implemented.**
- No confirm/import action yet. **Implemented.**

### I2 — Confirm import

- Add confirm import endpoint.
- Create `FinancialTransaction` rows from valid `IngestionRecord` rows.
- Use `origin = FILE_IMPORT`.
- Explicitly apply Rule Engine `FILL_EMPTY_ONLY` unless a later design finds a clear reason not to.
- Tests for creation, ownership, counters, linking, duplicates, and Rule Engine behavior.

### I3 — Duplicate/idempotency improvements

- Define strict file-level and row-level idempotency.
- Detect duplicate existing transactions if product wants it.
- Decide whether repeated checksum blocks or only warns.

### I4 — FinancialAccount shortcut

- Add "Import transactions" from account detail.
- Reuse same backend endpoint and preview UI.
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
- multipart preview submit.
- preview summary renders counts.
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

1. Repeated checksum for the same account is warning-only in I1; it does not block preview.
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
- I2 should explicitly apply Rule Engine `FILL_EMPTY_ONLY` during confirm import unless a later design finds a clear reason not to.
