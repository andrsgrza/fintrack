# CSV Ingestion UI/domain organization audit

## 1. Executive summary

The current CSV ingestion architecture has a healthy aggregate/workflow shape:

- `TransactionIngestion` is the workflow parent.
- `FileIngestion` is read-only file metadata.
- `IngestionRecord` is row-level review state.
- `FinancialTransaction` rows are created only during Confirm Import from valid ingestion records.

The canonical product flow is the custom CSV workflow:

1. create persisted CSV preview;
2. review persisted rows;
3. edit normalized rows for `VALID` / `REJECTED`;
4. enable or disable rows;
5. confirm import;
6. show completed review as read-only.

The main inconsistency is UI organization: generated CRUD pages for `TransactionIngestion`, `FileIngestion`, and `IngestionRecord` are still exposed in the normal Entities menu. Backend rules already block or guard many dangerous operations, but the generated UI still suggests that users can directly create/edit lifecycle records, file metadata, row status, raw JSON, errors, and transaction links.

Before adding a FinancialAccount import shortcut or Rule Engine behavior for imports, the product UI should be clarified so users enter and continue imports through the custom TransactionIngestion CSV workflow, not through generated child CRUD pages.

## 2. Field mutability matrix

### TransactionIngestion

| Field                   | Classification                         | Current behavior                                                                                                                               |
| ----------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                    | Immutable identity                     | Assigned by the database.                                                                                                                      |
| `ingestionType`         | Immutable after creation               | CSV preview creates `FILE`; should not change afterward.                                                                                       |
| `status`                | System-owned mutable                   | Preview sets `READY` or `PARTIALLY_READY`; row actions recalculate readiness; confirm import sets `COMPLETED`; failure paths may use `FAILED`. |
| `sourceLabel`           | System metadata                        | Preview uses `Canonical CSV: <filename>`; should not be normal product-user editable.                                                          |
| `startedAt`             | System-owned immutable                 | Set during preview creation.                                                                                                                   |
| `completedAt`           | System-owned lifecycle timestamp       | Set when preview parsing/persistence completes; updated when confirm import completes.                                                         |
| `recordsReceived`       | Derived/recalculated                   | Total persisted records.                                                                                                                       |
| `recordsCreated`        | Derived/recalculated                   | Count of `IMPORTED` records after confirm import.                                                                                              |
| `recordsSkipped`        | Derived/recalculated                   | Count of `DISABLED` + `SKIPPED_DUPLICATE`.                                                                                                     |
| `recordsRejected`       | Derived/recalculated                   | Count of `REJECTED` + `FAILED`.                                                                                                                |
| `errorMessage`          | System-owned mutable                   | Used for failed/error lifecycle states.                                                                                                        |
| `createdAt`             | Server-owned immutable                 | Set on create.                                                                                                                                 |
| `account`               | Relationship, immutable after creation | Preview resolves an account accessible to the current user.                                                                                    |
| `fileIngestion`         | Child relationship                     | Created together with CSV preview.                                                                                                             |
| `apiIngestion`          | Child relationship                     | Not used by CSV ingestion.                                                                                                                     |
| `financialTransactions` | Child relationship                     | Created by Confirm Import from valid rows.                                                                                                     |
| `records`               | Child relationship                     | Created during preview; mutated through contextual row-review actions.                                                                         |

Lifecycle changes today:

- Preview creation creates the parent, file metadata, and records.
- Enable row revalidates a disabled record and recalculates parent readiness/counts.
- Disable row marks a valid/rejected record as disabled and recalculates parent readiness/counts.
- Edit row updates normalized row data, derives validation status/errors/amount/flow, and recalculates parent readiness/counts.
- Confirm Import creates `FinancialTransaction` records from `VALID` rows, marks those records `IMPORTED`, and marks the parent `COMPLETED`.
- Delete `TransactionIngestion` deletes associated `FileIngestion`, `ApiIngestion`, `IngestionRecords`, related created `FinancialTransactions`, and join rows in the safe cleanup order.

### FileIngestion

| Field                  | Classification           | Current behavior                                    |
| ---------------------- | ------------------------ | --------------------------------------------------- |
| `id`                   | Immutable identity       | Assigned by the database.                           |
| `originalFilename`     | Read-only metadata       | Derived from the uploaded filename.                 |
| `fileType`             | Read-only metadata       | CSV preview sets `CSV`.                             |
| `contentType`          | Read-only metadata       | Comes from the multipart upload.                    |
| `fileSizeBytes`        | Read-only metadata       | Derived from uploaded bytes length.                 |
| `checksum`             | Derived metadata         | SHA-256 of uploaded file bytes.                     |
| `storageKey`           | Reserved metadata        | Currently `null`; durable file storage is deferred. |
| `parserName`           | Read-only metadata       | `fintrack-canonical-csv`.                           |
| `parserVersion`        | Read-only metadata       | `1.0`.                                              |
| `statementStartDate`   | Derived from parsed file | Minimum valid `transactionDate` in parsed rows.     |
| `statementEndDate`     | Derived from parsed file | Maximum valid `transactionDate` in parsed rows.     |
| `transactionIngestion` | Required relationship    | Parent `TransactionIngestion`.                      |
| `createdAt`            | Server-owned immutable   | Set during preview creation.                        |

No `FileIngestion` field should be editable from product UI. It is metadata about an uploaded file, not a user-managed resource.

### IngestionRecord

| Field                        | Classification                       | Current behavior                                                                                                                                          |
| ---------------------------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`                         | Immutable identity                   | Assigned by the database.                                                                                                                                 |
| `recordIndex`                | Immutable row identity               | CSV row index persisted during preview.                                                                                                                   |
| `externalRecordId`           | Normalized metadata                  | Derived from normalized `externalReference`, when present.                                                                                                |
| `status`                     | Validation/review state              | Preview sets `VALID` or `REJECTED`; disable sets `DISABLED`; enable revalidates to `VALID` or `REJECTED`; confirm sets imported valid rows to `IMPORTED`. |
| `rawData.raw`                | Immutable original input             | Snapshot of original CSV row values.                                                                                                                      |
| `rawData.normalized`         | Review-editable normalized candidate | Edited by users through the custom review row editor.                                                                                                     |
| `rawData.errors`             | Derived/recalculated                 | Rebuilt by the validation pipeline.                                                                                                                       |
| `rawData.warnings`           | Derived/recalculated                 | Rebuilt by the validation pipeline.                                                                                                                       |
| `rawData.review`             | Review metadata                      | Added/updated when normalized row values are edited.                                                                                                      |
| `errorCode` / `errorMessage` | Derived validation summary           | First validation error for display/search; cleared when valid/disabled.                                                                                   |
| `createdAt`                  | Server-owned immutable               | Set during preview creation.                                                                                                                              |
| `financialTransaction`       | Relationship created on confirm      | `null` until confirm import; then points to the created transaction for `IMPORTED` rows.                                                                  |
| `transactionIngestion`       | Parent relationship immutable        | Parent ingestion.                                                                                                                                         |

Confirmed intended behavior:

- `rawData.raw` is immutable.
- Users edit `rawData.normalized`, not `rawData.raw`.
- `amount`, `flow`, `status`, `errorCode`, and `errorMessage` are derived from the normalized candidate.
- `DISABLED` records are not editable; they must be enabled before editing.
- `IMPORTED` records are read-only.

## 3. UI route/view inventory

### TransactionIngestion views

| Route                                     | Component                                | Origin                     | Current role                     | Actions/fields                                                                                             |
| ----------------------------------------- | ---------------------------------------- | -------------------------- | -------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `/transaction-ingestion`                  | `transaction-ingestion.tsx`              | JHipster generated, edited | Mixed: list plus CSV entry point | Shows ingestion fields; has custom file import button, but also generated create/edit/delete actions.      |
| `/transaction-ingestion/new`              | `transaction-ingestion-update.tsx`       | Generated, edited          | Mostly debug/admin               | Generic create path; not the canonical CSV workflow.                                                       |
| `/transaction-ingestion/:id`              | `transaction-ingestion-detail.tsx`       | Generated                  | Mostly debug/admin               | Shows generic detail fields; does not serve as the custom review page.                                     |
| `/transaction-ingestion/:id/edit`         | `transaction-ingestion-update.tsx`       | Generated, edited          | Mostly debug/admin               | Exposes lifecycle/status/counter-style fields. Risky for product users.                                    |
| `/transaction-ingestion/:id/delete`       | Delete dialog                            | Generated                  | Useful parent cleanup            | Deletes parent and related ingestion/file/record/created transaction data through backend orchestration.   |
| `/transaction-ingestion/file-preview/new` | `transaction-ingestion-file-preview.tsx` | Custom CSV feature         | Canonical product UI             | Upload CSV, choose account, create persisted preview.                                                      |
| `/transaction-ingestion/:id/file-preview` | `transaction-ingestion-file-preview.tsx` | Custom CSV feature         | Canonical product UI             | Review rows, edit normalized values, enable/disable rows, confirm import, show completed read-only review. |

### FileIngestion views

| Route                        | Component                   | Origin    | Current role             | Actions/fields                                                           |
| ---------------------------- | --------------------------- | --------- | ------------------------ | ------------------------------------------------------------------------ |
| `/file-ingestion`            | `file-ingestion.tsx`        | Generated | Debug/admin only         | Lists metadata records.                                                  |
| `/file-ingestion/new`        | `file-ingestion-update.tsx` | Generated | Should not be product UI | Exposes metadata creation manually.                                      |
| `/file-ingestion/:id`        | `file-ingestion-detail.tsx` | Generated | Debug/admin only         | Shows file metadata. Metadata is already embedded in review UI.          |
| `/file-ingestion/:id/edit`   | `file-ingestion-update.tsx` | Generated | Should not be product UI | Suggests editable metadata; conceptually incorrect.                      |
| `/file-ingestion/:id/delete` | Delete dialog               | Generated | Not canonical            | Backend blocks direct delete; cleanup belongs to `TransactionIngestion`. |

### IngestionRecord views

| Route                          | Component                     | Origin    | Current role             | Actions/fields                                                                     |
| ------------------------------ | ----------------------------- | --------- | ------------------------ | ---------------------------------------------------------------------------------- |
| `/ingestion-record`            | `ingestion-record.tsx`        | Generated | Debug/admin only         | Lists persisted row records.                                                       |
| `/ingestion-record/new`        | `ingestion-record-update.tsx` | Generated | Should not be product UI | Exposes direct row creation.                                                       |
| `/ingestion-record/:id`        | `ingestion-record-detail.tsx` | Generated | Debug/admin only         | Shows row state.                                                                   |
| `/ingestion-record/:id/edit`   | `ingestion-record-update.tsx` | Generated | Should not be product UI | Can expose status/raw/errors/relationships outside the contextual review workflow. |
| `/ingestion-record/:id/delete` | Delete dialog                 | Generated | Not canonical            | Backend blocks direct delete; cleanup belongs to `TransactionIngestion`.           |

## 4. UI Composition alignment

The custom CSV workflow follows the intended composition rules documented in `docs/UI-COMPOSITION.md`:

- `FileIngestion` is shown as read-only metadata inside the TransactionIngestion review page.
- `IngestionRecord` is managed through a contextual review table, not through standalone product CRUD.
- The TransactionIngestion review page is a workflow page, not a generated update form.
- Completed ingestions are read-only.
- Full child CRUD is not embedded arbitrarily into parent CRUD.

The gap is not the custom workflow. The gap is navigation and page ownership: generated CRUD pages still appear as normal entity pages. That weakens the product model because users can discover screens that do not match the domain language.

Generated CRUD pages may still be useful as development/debug tools, but they should not be treated as canonical user-facing product UI.

## 5. Risks/inconsistencies

1. `TransactionIngestion` generated update suggests lifecycle fields can be edited by users, even though status, counters, and timestamps are workflow/system-owned.

2. `FileIngestion` generated create/edit contradicts read-only metadata semantics.

3. `IngestionRecord` generated create/edit contradicts contextual row-review semantics and can expose raw JSON, status, errors, and transaction links outside the ingestion workflow.

4. `TransactionIngestion` detail does not naturally route users to the CSV review page for FILE ingestions.

5. `FileIngestion` and `IngestionRecord` are still visible in the normal Entities menu, even though their product meaning is child metadata/review state.

6. Backend has some admin-access branches in generic services. For future Rule Engine import behavior, avoid designing any admin cross-user evaluation behavior; rule application should remain scoped to the current user/account owner as already decided.

7. Rule Engine behavior for imports should not be mixed into preview/edit-row validation. Preview validates CSV rows. Rule application belongs at transaction creation time during Confirm Import.

## 6. Recommendations

Canonical product UI should be:

- `/transaction-ingestion/file-preview/new`
- `/transaction-ingestion/{id}/file-preview`
- a cleaned `TransactionIngestion` list that links FILE ingestions to the review page.

Generated/debug-only UI should be:

- `FileIngestion` list/detail/update/delete;
- `IngestionRecord` list/detail/update/delete;
- generic `TransactionIngestion` create/update;
- possibly generic `TransactionIngestion` detail, unless it is repurposed to link clearly to the workflow page.

Recommended cleanup before adding the FinancialAccount shortcut:

- Hide `FileIngestion` and `IngestionRecord` from normal navigation.
- On the `TransactionIngestion` list, prefer a “Review” action for FILE ingestions.
- Hide or demote generic “Create Transaction Ingestion”; users should create CSV ingestions through “New File Import”.
- Preserve parent delete behavior because backend cleanup is correctly centralized on `TransactionIngestion`.

Recommended FinancialAccount shortcut shape:

- Add “Import CSV” from FinancialAccount detail.
- Route to the existing upload page with the account preselected.
- Reuse the same preview/review/confirm flow.
- Do not create a new FileIngestion flow.
- Do not duplicate parser/review logic.

Recommended Rule Engine import placement:

- Apply rules during Confirm Import when creating each `FinancialTransaction`.
- Use the same `FILL_EMPTY_ONLY` contract as manual create.
- Do not apply rules during preview creation.
- Do not apply rules during row edit.
- Do not persist evaluation results in this phase.
- Do not mutate `rawData.raw`.
- Keep `rawData.normalized` as the reviewed import candidate.

## 7. Suggested next implementation slices

1. Navigation/product cleanup:

   - remove or hide `FileIngestion` and `IngestionRecord` from the normal Entities menu;
   - change TransactionIngestion list actions so FILE ingestions go to `/transaction-ingestion/{id}/file-preview`;
   - hide generic create/edit where they are not product-safe.

2. FinancialAccount import shortcut:

   - add an Import CSV action on FinancialAccount detail;
   - preselect the account in CSV upload;
   - preserve the existing custom review flow.

3. Rule Engine import design doc update:

   - explicitly document that import rule application happens during Confirm Import;
   - clarify that preview/review remains CSV validation only.

4. Rule Engine import implementation:
   - for each `VALID` ingestion row, build the `FinancialTransaction` draft;
   - evaluate matching rules for the transaction owner;
   - apply category/tags in `FILL_EMPTY_ONLY`;
   - save the transaction and mark the record `IMPORTED`.

## Files inspected

Docs inspected:

- `docs/UI-COMPOSITION.md`
- `docs/CSV-INGESTION-V1-DESIGN.md`
- `docs/DOMAIN-RULES.md`
- `docs/VALIDATIONS.md`
- `docs/IMPLEMENTATION.md`
- `docs/TESTING.md`

Backend files inspected:

- `src/main/java/com/fintrack/app/domain/TransactionIngestion.java`
- `src/main/java/com/fintrack/app/domain/FileIngestion.java`
- `src/main/java/com/fintrack/app/domain/IngestionRecord.java`
- `src/main/java/com/fintrack/app/domain/enumeration/IngestionStatus.java`
- `src/main/java/com/fintrack/app/domain/enumeration/IngestionRecordStatus.java`
- `src/main/java/com/fintrack/app/domain/enumeration/IngestionType.java`
- `src/main/java/com/fintrack/app/service/TransactionIngestionService.java`
- `src/main/java/com/fintrack/app/service/FileIngestionService.java`
- `src/main/java/com/fintrack/app/service/IngestionRecordService.java`
- `src/main/java/com/fintrack/app/service/CsvIngestionPreviewService.java`
- `src/main/java/com/fintrack/app/service/CsvIngestionRecordReviewService.java`
- `src/main/java/com/fintrack/app/service/CsvIngestionConfirmImportService.java`
- `src/main/java/com/fintrack/app/service/CsvIngestionReadinessService.java`
- `src/main/java/com/fintrack/app/web/rest/TransactionIngestionResource.java`
- `src/main/java/com/fintrack/app/web/rest/FileIngestionResource.java`
- `src/main/java/com/fintrack/app/web/rest/IngestionRecordResource.java`

Frontend files inspected:

- `src/main/webapp/app/entities/transaction-ingestion/index.tsx`
- `src/main/webapp/app/entities/transaction-ingestion/transaction-ingestion.tsx`
- `src/main/webapp/app/entities/transaction-ingestion/transaction-ingestion-detail.tsx`
- `src/main/webapp/app/entities/transaction-ingestion/transaction-ingestion-update.tsx`
- `src/main/webapp/app/entities/transaction-ingestion/transaction-ingestion-file-preview.tsx`
- `src/main/webapp/app/entities/transaction-ingestion/transaction-ingestion-file-preview.spec.tsx`
- `src/main/webapp/app/entities/file-ingestion/index.tsx`
- `src/main/webapp/app/entities/file-ingestion/file-ingestion.tsx`
- `src/main/webapp/app/entities/file-ingestion/file-ingestion-detail.tsx`
- `src/main/webapp/app/entities/file-ingestion/file-ingestion-update.tsx`
- `src/main/webapp/app/entities/ingestion-record/index.tsx`
- `src/main/webapp/app/entities/ingestion-record/ingestion-record.tsx`
- `src/main/webapp/app/entities/ingestion-record/ingestion-record-detail.tsx`
- `src/main/webapp/app/entities/ingestion-record/ingestion-record-update.tsx`
- `src/main/webapp/app/entities/menu.tsx`
- `src/main/webapp/app/entities/routes.tsx`
