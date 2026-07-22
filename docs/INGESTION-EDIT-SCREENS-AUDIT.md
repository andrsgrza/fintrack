# FINTRACK Ingestion Edit Screens Audit

## 1. Executive summary

After the CSV workflow route cleanup, the canonical product flow is correctly centered on `TransactionIngestion`:

- `TransactionIngestion` is the workflow parent.
- `FileIngestion` is server-derived FILE metadata.
- `IngestionRecord` is the persisted review/import row.
- `FinancialTransaction` rows are created only by Confirm Import.
- The Rule Engine is not invoked during CSV upload, review, or import v1.

The intended normal workflow is:

```text
/transaction-ingestion/new
  -> POST /api/transaction-ingestions/file
  -> /transaction-ingestion/:id
  -> GET /api/transaction-ingestions/:id/workflow
  -> row edit/enable/disable
  -> confirm import
```

However, several generated CRUD screens remain visible:

- `/transaction-ingestion/:id/edit`
- `/file-ingestion`
- `/file-ingestion/new`
- `/file-ingestion/:id/edit`
- `/ingestion-record`
- `/ingestion-record/new`
- `/ingestion-record/:id/edit`

Recommended direction:

- `TransactionIngestion` should be the only normal product entry point for CSV ingestion.
- `TransactionIngestion` edit should not exist as normal product UI; today it exposes lifecycle/system fields.
- `FileIngestion` standalone screens should be hidden/debug-only or read-only metadata.
- `IngestionRecord` standalone CRUD should be hidden from normal navigation/actions; row edits should happen only inside `/transaction-ingestion/:id`.
- Existing backend CRUD endpoints can remain temporarily, but should be restricted or converted to command-style endpoints as the workflow hardens.

No code or docs behavior was changed as part of this audit.

## 2. Current route/action inventory

### TransactionIngestion

| Route                               | Component                                   | Type               | Current state                  |
| ----------------------------------- | ------------------------------------------- | ------------------ | ------------------------------ |
| `/transaction-ingestion`            | `transaction-ingestion.tsx`                 | generated/modified | Visible product list           |
| `/transaction-ingestion/new`        | `transaction-ingestion-update.tsx`          | modified generated | Canonical FILE create workflow |
| `/transaction-ingestion/:id`        | `transaction-ingestion-workflow-detail.tsx` | custom             | Canonical workflow detail      |
| `/transaction-ingestion/:id/edit`   | `transaction-ingestion-update.tsx`          | generated/modified | Lifecycle/counter edit form    |
| `/transaction-ingestion/:id/delete` | delete dialog                               | generated          | Deletes whole ingestion tree   |

Visible actions:

- Entity menu exposes `TransactionIngestion`.
- List exposes `View`, `Edit`, `Delete`, `Create new Transaction Ingestion`, and `New File Import`.
- Workflow detail exposes `Edit`.
- Normal product flow uses `/transaction-ingestion/new` and `/transaction-ingestion/:id`.

Create screen visible fields:

- Account selector.
- Ingestion Type selector.
- CSV file input for `FILE`.
- API placeholder for `API`.
- Calls `POST /api/transaction-ingestions/file`.

Edit screen visible fields:

- `id` read-only.
- `ingestionType` disabled.
- `status` editable.
- `startedAt` read-only.
- `completedAt` visible/editable in UI, but backend rejects client change unless same/no-op.
- `recordsReceived` editable.
- `recordsCreated` editable.
- `recordsSkipped` editable.
- `recordsRejected` editable.
- `errorMessage` editable.
- `createdAt` read-only.
- `sourceLabel` editable.
- `account` disabled.
- Saves through `PUT /api/transaction-ingestions/{id}`.

Assessment: this edit screen is weak as product UI because it exposes lifecycle/system fields rather than meaningful user actions.

### FileIngestion

| Route                        | Component                   | Type               | Current state                                    |
| ---------------------------- | --------------------------- | ------------------ | ------------------------------------------------ |
| `/file-ingestion`            | `file-ingestion.tsx`        | generated          | Visible standalone list                          |
| `/file-ingestion/new`        | `file-ingestion-update.tsx` | modified generated | Secondary/debug upload to pending parent         |
| `/file-ingestion/:id`        | `file-ingestion-detail.tsx` | generated          | Metadata detail                                  |
| `/file-ingestion/:id/edit`   | `file-ingestion-update.tsx` | generated/modified | Metadata edit form                               |
| `/file-ingestion/:id/delete` | delete dialog               | generated          | UI exposes delete; backend rejects direct delete |

Visible actions:

- Entity menu exposes `FileIngestion`.
- List exposes `Create`, `View`, `Edit`, `Delete`.
- Detail exposes `Edit`.
- Normal product flow should not normally enter here, except `/file-ingestion/new` as a secondary/debug parent upload path.

Create screen visible fields:

- Parent `TransactionIngestion` selector filtered to pending FILE parents without file metadata.
- CSV file input.
- Calls `POST /api/transaction-ingestions/{id}/file-ingestion`.
- Redirects to `/transaction-ingestion/{id}`.

Edit screen visible fields:

- `id` read-only.
- `originalFilename`
- `fileType`
- `contentType`
- `fileSizeBytes`
- `checksum`
- `storageKey`
- `parserName`
- `parserVersion`
- `statementStartDate`
- `statementEndDate`
- `createdAt` read-only.
- `transactionIngestion` disabled.
- Saves through `PUT /api/file-ingestions/{id}`.

Backend only allows changing:

- `statementStartDate`
- `statementEndDate`

Backend rejects changes to:

- filename;
- file type;
- content type;
- size;
- checksum;
- storage key;
- parser name/version;
- parent transaction ingestion;
- `createdAt`.

Assessment: metadata detail can be useful, but edit is misleading because most fields are server-derived and immutable.

### IngestionRecord

| Route                          | Component                     | Type               | Current state                                    |
| ------------------------------ | ----------------------------- | ------------------ | ------------------------------------------------ |
| `/ingestion-record`            | `ingestion-record.tsx`        | generated          | Visible standalone list                          |
| `/ingestion-record/new`        | `ingestion-record-update.tsx` | generated/modified | Standalone create                                |
| `/ingestion-record/:id`        | `ingestion-record-detail.tsx` | generated          | Raw/debug detail                                 |
| `/ingestion-record/:id/edit`   | `ingestion-record-update.tsx` | generated/modified | Standalone record edit                           |
| `/ingestion-record/:id/delete` | delete dialog                 | generated          | UI exposes delete; backend rejects direct delete |

Visible actions:

- Entity menu exposes `IngestionRecord`.
- List exposes `Create`, `View`, `Edit`, `Delete`.
- Detail exposes `Edit`.
- Normal product flow should not use these screens. Product row edit is contextual inside `/transaction-ingestion/:id`.

Standalone edit visible fields:

- `id` read-only.
- `recordIndex` read-only in edit, editable in create.
- `externalRecordId`
- `status`
- `rawData`
- `errorCode`
- `errorMessage`
- `createdAt` read-only.
- `financialTransaction` disabled in edit, selectable in create.
- `transactionIngestion` disabled in edit, selectable in create.
- Saves through `PUT /api/ingestion-records/{id}`.

Contextual workflow row edit uses:

- `PATCH /api/transaction-ingestions/{ingestionId}/records/{recordId}`
- normalized row candidate fields only;
- full row revalidation;
- derived status/amount/flow/errors;
- fixed parent and original raw audit payload.

Assessment: standalone generated edit conflicts conceptually with contextual row review semantics.

## 3. Field mutability matrix

### TransactionIngestion

| Field                   | Classification                                                  |
| ----------------------- | --------------------------------------------------------------- |
| `id`                    | server-owned                                                    |
| `ingestionType`         | immutable after creation                                        |
| `status`                | derived/workflow-owned; should change through workflow commands |
| `sourceLabel`           | derived from source/file; debug-only candidate                  |
| `startedAt`             | server-owned                                                    |
| `completedAt`           | server-owned                                                    |
| `recordsReceived`       | derived/recalculated                                            |
| `recordsCreated`        | derived/recalculated                                            |
| `recordsSkipped`        | derived/recalculated                                            |
| `recordsRejected`       | derived/recalculated                                            |
| `errorMessage`          | workflow-owned; failed state only                               |
| `createdAt`             | server-owned                                                    |
| `account`               | fixed parent/owner relationship                                 |
| `fileIngestion`         | child metadata, server-derived                                  |
| `apiIngestion`          | TBD child metadata                                              |
| `records`               | child collection managed by workflow                            |
| `financialTransactions` | created by Confirm Import                                       |

Product-editable today: none clearly.

### FileIngestion

| Field                  | Classification                                     |
| ---------------------- | -------------------------------------------------- |
| `id`                   | server-owned                                       |
| `originalFilename`     | derived/immutable                                  |
| `fileType`             | derived/immutable                                  |
| `contentType`          | derived/immutable                                  |
| `fileSizeBytes`        | derived/immutable                                  |
| `checksum`             | derived/immutable                                  |
| `storageKey`           | derived/immutable                                  |
| `parserName`           | derived/immutable                                  |
| `parserVersion`        | derived/immutable                                  |
| `statementStartDate`   | currently backend-mutable; product decision needed |
| `statementEndDate`     | currently backend-mutable; product decision needed |
| `transactionIngestion` | fixed parent relationship                          |
| `createdAt`            | server-owned                                       |

Product-editable today: possibly statement dates only, if the product wants user-correctable statement metadata.

### IngestionRecord

| Field                  | Classification                               |
| ---------------------- | -------------------------------------------- |
| `id`                   | server-owned                                 |
| `recordIndex`          | derived/immutable                            |
| `externalRecordId`     | derived/immutable                            |
| `status`               | derived/workflow action-owned                |
| `rawData.raw`          | original audit payload; immutable            |
| `rawData.normalized`   | derived/recalculated by review edit pipeline |
| `rawData.errors`       | derived/recalculated                         |
| `rawData.warnings`     | derived/recalculated                         |
| `rawData.review`       | server-owned review metadata                 |
| `errorCode`            | derived summary                              |
| `errorMessage`         | derived summary                              |
| `createdAt`            | server-owned                                 |
| `financialTransaction` | set by Confirm Import only                   |
| `transactionIngestion` | fixed parent relationship                    |

Product-editable today: normalized row candidate fields only, and only through contextual workflow detail row editor.

## 4. Screen-by-screen findings

### TransactionIngestion edit

Recommendation: hide from normal product flow, likely redirect `/transaction-ingestion/:id/edit` to `/transaction-ingestion/:id`.

Reasons:

- It exposes lifecycle fields and counters.
- It duplicates/conflicts with canonical workflow detail.
- Meaningful user actions already exist as command-style actions:
  - upload file;
  - edit row;
  - enable/disable row;
  - confirm import;
  - delete workflow.
- Backend protects immutable fields (`account`, `ingestionType`, `startedAt`, `createdAt`, `completedAt`) but still allows direct update of status/counters/source/error when valid.
- That is too technical for normal UI.

Valid editable fields today: none for product UI.

Future action:

- Hide Edit button from TransactionIngestion list.
- Hide Edit button from workflow detail.
- Redirect `/transaction-ingestion/:id/edit` to `/transaction-ingestion/:id` or render a read-only/debug-only page.

### FileIngestion edit

Recommendation: hide edit from normal flow; keep detail as read-only metadata.

Reasons:

- File metadata is server-derived.
- Backend rejects changes to most fields.
- UI displays immutable fields as editable, which is misleading.
- Direct delete is exposed but backend rejects it.

Possible future choices:

1. Strong product stance: remove/redirect edit route to parent workflow.
2. Softer stance: keep edit but expose only `statementStartDate` and `statementEndDate`.

Preferred stance: strong product stance unless statement dates get an explicit user correction story.

### IngestionRecord edit

Recommendation: hide standalone CRUD from normal navigation/actions; keep contextual row edit inside `/transaction-ingestion/:id`.

Reasons:

- Standalone edit exposes `status`, `rawData`, `errorCode`, `errorMessage`.
- Product row edit should reprocess normalized values through `CsvIngestionRecordReviewService`.
- Standalone CRUD can conceptually bypass clean command semantics, even if backend blocks many dangerous edits.
- Backend freezes records after parent final status and blocks rawData/parent/recordIndex/externalRecordId changes, but still allows direct mutation of status/error fields in non-final parents through CRUD.

Standalone detail can remain debug/admin-only if useful for raw JSON audit inspection, but it should not be normal product UI.

## 5. Backend endpoint findings

### TransactionIngestion endpoints

| Endpoint                                                           | Current use                                   | Recommendation                                                          |
| ------------------------------------------------------------------ | --------------------------------------------- | ----------------------------------------------------------------------- |
| `POST /api/transaction-ingestions`                                 | Generic CRUD create                           | Debug/internal or restricted; product FILE create should include upload |
| `POST /api/transaction-ingestions/file`                            | Canonical FILE create                         | Keep                                                                    |
| `POST /api/transaction-ingestions/{id}/file-ingestion`             | Secondary/debug upload to pending FILE parent | Keep for now, document/debug-only                                       |
| `GET /api/transaction-ingestions/{id}/workflow`                    | Canonical workflow read                       | Keep                                                                    |
| `POST /api/transaction-ingestions/{id}/records/{recordId}/disable` | Product command                               | Keep                                                                    |
| `POST /api/transaction-ingestions/{id}/records/{recordId}/enable`  | Product command                               | Keep                                                                    |
| `PATCH /api/transaction-ingestions/{id}/records/{recordId}`        | Product contextual row edit                   | Keep                                                                    |
| `POST /api/transaction-ingestions/{id}/confirm`                    | Product confirm import                        | Keep                                                                    |
| `PUT/PATCH /api/transaction-ingestions/{id}`                       | Generic lifecycle update                      | Restrict or remove from normal UI                                       |
| `DELETE /api/transaction-ingestions/{id}`                          | Whole workflow delete                         | Keep if deletion is intended                                            |
| `GET /api/transaction-ingestions`                                  | Workflow list                                 | Keep                                                                    |

### FileIngestion endpoints

| Endpoint                              | Current use                              | Recommendation                                        |
| ------------------------------------- | ---------------------------------------- | ----------------------------------------------------- |
| `POST /api/file-ingestions`           | Generic metadata create                  | Not product flow; restrict/remove from product UI     |
| `PUT/PATCH /api/file-ingestions/{id}` | Statement date-only mutation in practice | Keep only if statement correction is product-approved |
| `GET /api/file-ingestions`            | Standalone list                          | Debug/admin-only at most                              |
| `GET /api/file-ingestions/{id}`       | Metadata detail                          | Could remain read-only/debug                          |
| `DELETE /api/file-ingestions/{id}`    | Direct delete endpoint                   | Backend rejects; UI should not expose                 |

### IngestionRecord endpoints

| Endpoint                                | Current use            | Recommendation                                                 |
| --------------------------------------- | ---------------------- | -------------------------------------------------------------- |
| `POST /api/ingestion-records`           | Standalone create      | Not product flow; records should be created by parser/workflow |
| `PUT/PATCH /api/ingestion-records/{id}` | Generic record update  | Not product flow; conflicts with contextual row edit           |
| `GET /api/ingestion-records`            | Standalone list        | Debug/admin-only at most                                       |
| `GET /api/ingestion-records/{id}`       | Raw detail             | Could remain debug/read-only                                   |
| `DELETE /api/ingestion-records/{id}`    | Direct delete endpoint | Backend rejects; UI should not expose                          |

## 6. UI composition alignment

Aligned with `docs/UI-COMPOSITION.md`:

- `/transaction-ingestion/:id` uses contextual workflow detail instead of embedding generated CRUD.
- File metadata is embedded read-only in workflow detail.
- Review rows are shown as a table, not a giant embedded generated CRUD collection.
- Contextual row edit hides parent relationship and derived fields.

Misaligned:

- `FileIngestion` and `IngestionRecord` generated CRUD pages are still visible in the Entities menu.
- Generated list/detail pages expose `Edit/Delete` actions that do not match the domain workflow.
- `TransactionIngestion` list/detail still expose generic Edit even though canonical workflow actions are command-style.
- Standalone `IngestionRecord` update duplicates/conflicts with workflow row edit.
- Standalone `FileIngestion` update shows immutable derived metadata as editable UI, relying on backend to reject changes.

Documentation note:

- `docs/CSV-INGESTION-V1-DESIGN.md` still contains a stale “Preview screen” heading in the UI section. The surrounding content is mostly workflow-aligned, but the heading should be renamed in a docs cleanup slice.

## 7. Suggested implementation slices

1. Hide unsafe normal UI actions:

   - remove/hide Edit from `TransactionIngestion` list/workflow detail;
   - remove/hide Edit/Delete from `FileIngestion` list/detail;
   - remove/hide Create/Edit/Delete from `IngestionRecord` list/detail.

2. Menu cleanup:

   - keep `TransactionIngestion` in the normal Entities menu;
   - remove/hide `FileIngestion` and `IngestionRecord` from the normal menu, or move them to debug/admin if such a pattern exists.

3. Route behavior:

   - redirect `/transaction-ingestion/:id/edit` to `/transaction-ingestion/:id`;
   - redirect `/file-ingestion/:id/edit` to parent workflow or file detail;
   - redirect `/ingestion-record/:id/edit` to parent workflow when possible.

4. Backend hardening:

   - restrict `POST /api/file-ingestions`;
   - restrict generic `POST/PUT/PATCH /api/ingestion-records`;
   - decide whether `PUT/PATCH /api/transaction-ingestions/{id}` should survive outside admin/debug.

5. Docs/tests:
   - update UI composition/docs to state `TransactionIngestion` is the only normal workflow entry.
   - add frontend tests that hidden actions/routes stay hidden.
   - add backend tests if endpoints are restricted.
