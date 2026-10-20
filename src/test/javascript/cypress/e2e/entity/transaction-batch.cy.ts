describe('Transaction Batch product workflow', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  let accountId: number;
  let categoryId: number;
  let tagId: number;
  let tagName: string;
  let batchId: number | undefined;
  const createdDescriptionRuleIds: number[] = [];
  const createdTransactionRuleIds: number[] = [];

  const csvHeader = 'transactionDate,postingDate,description,signedAmount,currency,externalReference,notes';

  beforeEach(() => {
    batchId = undefined;
    createdDescriptionRuleIds.length = 0;
    createdTransactionRuleIds.length = 0;
    cy.login(username, password);
    cy.authenticatedRequest<{ id: number; name: string }>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('batch-account'),
        institutionName: 'E2E Bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 1000,
        initialBalanceDate: '2026-10-16',
        lastFourDigits: '1234',
        active: true,
      },
    }).then(({ body }) => {
      accountId = body.id;
    });
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/categories',
      body: {
        name: uniqueName('batch-category'),
        categoryType: 'EXPENSE',
        color: '#2463A5',
        active: true,
      },
    }).then(({ body }) => {
      categoryId = body.id;
    });
    cy.authenticatedRequest<{ id: number; name: string }>({
      method: 'POST',
      url: '/api/tags',
      body: {
        name: uniqueName('batch-tag'),
        color: '#3B82F6',
        active: true,
      },
    }).then(({ body }) => {
      tagId = body.id;
      tagName = body.name;
    });
  });

  afterEach(() => {
    createdDescriptionRuleIds.forEach(id => {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/description-normalization-rules/${id}`,
        failOnStatusCode: false,
      });
    });
    createdTransactionRuleIds.forEach(id => {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${id}`,
        failOnStatusCode: false,
      });
    });
    if (accountId) {
      cy.authenticatedRequest<{ id: number; version: number; accountId: number }[]>({
        method: 'GET',
        url: '/api/transaction-batches',
      }).then(({ body }) => {
        body
          .filter(batch => batch.accountId === accountId)
          .forEach(batch => {
            cy.authenticatedRequest({
              method: 'DELETE',
              url: `/api/transaction-batches/${batch.id}`,
              qs: { expectedBatchVersion: batch.version },
              failOnStatusCode: false,
            });
          });
      });
    }
  });

  const createOpenBatch = () => {
    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.location('pathname')
      .should('match', /\/transaction-batches\/\d+$/)
      .then(pathname => {
        const createdBatchId = Number(pathname.split('/').pop());
        expect(Number.isSafeInteger(createdBatchId)).to.equal(true);
        batchId = createdBatchId;
      });
  };

  const addCompleteManualRow = (description: string, amount = '125.50') => {
    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('.modal').should('contain', 'Añadir movimiento');
    cy.get('#batch-editor-transaction-date').type('2026-10-16');
    cy.get('#batch-editor-description').type(description);
    cy.get('#batch-editor-amount').type(amount);
    cy.get('[data-cy="transactionBatchEditorSave"]').click();
  };

  const openRowActions = (rowSelector: string) => {
    cy.get(rowSelector).within(() => {
      cy.get('[data-cy^="transactionBatchRowActions-"][data-cy$="Toggle"]').click();
    });
  };

  const createAutoApplyRules = (token: string, normalizedDescription: string) => {
    const descriptionRuleName = uniqueName('batch-normalization-rule');
    const classificationRuleName = uniqueName('batch-classification-rule');

    return cy
      .authenticatedRequest<{ id: number }>({
        method: 'POST',
        url: '/api/description-normalization-rules/configured',
        body: {
          name: descriptionRuleName,
          active: true,
          conditionOperator: 'ALL',
          resultingDescription: normalizedDescription,
          conditions: [{ operator: 'CONTAINS', value: token, caseSensitive: false, position: 0 }],
        },
      })
      .then(({ body: descriptionRule }) => {
        createdDescriptionRuleIds.push(descriptionRule.id);
        return cy
          .authenticatedRequest<{ id: number }>({
            method: 'POST',
            url: '/api/transaction-rules/configured',
            body: {
              name: classificationRuleName,
              description: 'Batch acceptance rule fixture',
              active: true,
              conditionLogic: 'ALL',
              resultingCategory: { id: categoryId },
              resultingTags: [{ id: tagId }],
              conditions: [
                { field: 'DESCRIPTION', operator: 'CONTAINS', value: normalizedDescription, secondValue: null, caseSensitive: false },
                { field: 'FLOW', operator: 'EQUALS', value: 'OUT', secondValue: null, caseSensitive: false },
              ],
            },
          })
          .then(({ body: classificationRule }) => {
            createdTransactionRuleIds.push(classificationRule.id);
            return {
              descriptionRule: { id: descriptionRule.id, name: descriptionRuleName },
              classificationRule: { id: classificationRule.id, name: classificationRuleName },
            };
          });
      });
  };

  const createManualClassificationRule = (token: string) => {
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/transaction-rules/configured',
      body: {
        name: uniqueName('batch-manual-classification-rule'),
        description: 'Manual batch preview fixture',
        active: true,
        conditionLogic: 'ALL',
        resultingCategory: { id: categoryId },
        resultingTags: [{ id: tagId }],
        conditions: [
          { field: 'DESCRIPTION', operator: 'CONTAINS', value: token, secondValue: null, caseSensitive: false },
          { field: 'FLOW', operator: 'EQUALS', value: 'OUT', secondValue: null, caseSensitive: false },
        ],
      },
    }).then(({ body }) => {
      createdTransactionRuleIds.push(body.id);
    });
  };

  const measureComparableCsvBatch = (rowCount: number, token: string) => {
    const appendAlias = `batchMetricAppend${rowCount}`;
    let measuredBatchId: number;
    let loadStartedAt = 0;

    cy.authenticatedRequest<{ id: number; version: number }>({
      method: 'POST',
      url: '/api/transaction-batches',
      body: { accountId },
    }).then(({ body }) => {
      measuredBatchId = body.id;
      return cy.authenticatedRequest({
        method: 'PUT',
        url: `/api/transaction-batches/${body.id}/settings`,
        body: {
          expectedBatchVersion: body.version,
          autoApplyDescriptions: false,
          autoApplyCategories: false,
          autoApplyTags: true,
          protectManualChanges: true,
        },
      });
    });

    cy.then(() => cy.visit(`/transaction-batches/${measuredBatchId}`));
    const csvRows = Array.from(
      { length: rowCount },
      (_, index) => `2026-10-17,,${token}-${index},-75.00,MXN,metric-${rowCount}-${index},CSV metric row ${index}`,
    );
    cy.intercept('POST', '/api/transaction-batches/*/append-csv').as(appendAlias);
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile(
      {
        contents: Cypress.Buffer.from([csvHeader, ...csvRows].join('\n')),
        fileName: `batch-metric-${rowCount}.csv`,
        mimeType: 'text/csv',
        lastModified: Date.now(),
      },
      { force: true },
    );
    cy.get('[data-cy="transactionBatchAppendCsv"]').click();
    cy.wait(`@${appendAlias}`).its('response.statusCode').should('eq', 200);
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', rowCount);

    cy.then(() => {
      loadStartedAt = Date.now();
      return cy.visit(`/transaction-batches/${measuredBatchId}`);
    });
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', rowCount);
    cy.get('[role="alert"]').should('not.exist');
    cy.window().then(window => {
      const resources = window.performance
        .getEntriesByType('resource')
        .map(entry => entry as PerformanceResourceTiming)
        .filter(entry => entry.name.includes('/api/'));
      const workspaceRequest = resources.find(entry => entry.name.endsWith(`/api/transaction-batches/${measuredBatchId}`));
      expect(workspaceRequest, `workspace resource timing for ${rowCount} rows`).to.not.equal(undefined);
      const renderMilliseconds = Date.now() - loadStartedAt;
      cy.task(
        'log',
        JSON.stringify({
          transactionBatchMeasurement: {
            candidates: rowCount,
            expectedTagAssignments: rowCount,
            observedRequests: {
              workspace: resources.filter(entry => entry.name.endsWith(`/api/transaction-batches/${measuredBatchId}`)).length,
              categories: resources.filter(entry => entry.name.includes('/api/categories/selectable')).length,
              tags: resources.filter(entry => entry.name.includes('/api/tags/selectable')).length,
              rulePreview: resources.filter(entry => entry.name.includes(`/api/transaction-batches/${measuredBatchId}/rule-preview`))
                .length,
            },
            transferBytes: workspaceRequest?.transferSize,
            encodedBodyBytes: workspaceRequest?.encodedBodySize,
            decodedBodyBytes: workspaceRequest?.decodedBodySize,
            loadAndRenderMilliseconds: renderMilliseconds,
          },
        }),
      );
    });

    cy.contains('button', 'Manual').click();
    cy.get('body').find('[data-cy^="transactionBatchRow-"]').should('have.length', 0);
    cy.contains('button', 'Todos').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', rowCount);
    cy.viewport(375, 667);
    cy.get('[data-cy^="transactionBatchMobileDetails-"]').first().click();
    cy.get('[data-cy^="transactionBatchMobileDetails-"]').first().should('have.attr', 'aria-expanded', 'true');
    cy.get('[data-cy^="transactionBatchRow-"]').first().find('[data-cy$="Chip"]').should('contain', tagName);
    cy.viewport(1200, 720);

    let persistedVersion: number;
    cy.then(() =>
      cy.authenticatedRequest<{ version: number; candidates: Array<{ tags: unknown[] }> }>({
        method: 'GET',
        url: `/api/transaction-batches/${measuredBatchId}`,
      }),
    ).then(({ body }) => {
      expect(body.candidates).to.have.length(rowCount);
      expect(body.candidates.reduce((total, candidate) => total + candidate.tags.length, 0)).to.equal(rowCount);
      persistedVersion = body.version;
    });
    cy.then(() =>
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-batches/${measuredBatchId}`,
        qs: { expectedBatchVersion: persistedVersion },
      }),
    )
      .its('status')
      .should('eq', 204);
    cy.then(() =>
      cy.authenticatedRequest({
        method: 'GET',
        url: `/api/transaction-batches/${measuredBatchId}`,
        failOnStatusCode: false,
      }),
    )
      .its('status')
      .should('eq', 404);
  };

  it('creates, reviews, publishes, and recovers an owned batch workspace', () => {
    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.location('pathname').should('match', /\/transaction-batches\/\d+$/);
    cy.location('pathname').then(pathname => {
      batchId = Number(pathname.split('/').pop());
    });

    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('not.exist');
    cy.get('[data-cy="batchEditorFlow-IN"]').click();
    cy.get('[data-cy="batchEditorFlow-IN"]').should('have.attr', 'aria-pressed', 'true');
    cy.screenshot('transaction-batch/14-income-flow-selector');
    cy.get('#batch-editor-transaction-date').type('2026-10-16');
    cy.get('#batch-editor-description').type('Movimiento de lote E2E');
    cy.get('#batch-editor-amount').type('125.50');
    cy.get('[data-cy="batchEditorFlow-OUT"]').click();
    cy.get('[data-cy="batchEditorFlow-OUT"]').should('have.attr', 'aria-pressed', 'true');
    cy.screenshot('transaction-batch/15-expense-flow-selector');
    cy.get('[data-cy="batchEditorFlow-IN"]').click();
    cy.get('[data-cy="batchEditorFlow-IN"]').should('have.attr', 'aria-pressed', 'true');
    cy.get('[data-cy="transactionBatchEditorSave"]').click();

    cy.get('[aria-label="Categoría para Movimiento de lote E2E"]').select(String(categoryId));
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Add"]').click();
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Option"]').contains('button', new RegExp('batch-tag')).click();
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Chip"]').should('contain', 'batch-tag');

    cy.get('[data-cy="transactionBatchPublish"]').should('not.be.disabled').click();
    cy.contains('.modal button', 'Crear transacciones').should('be.visible');
    cy.screenshot('transaction-batch/05-publication-confirmation');
    cy.contains('.modal button', 'Crear transacciones').click();
    cy.contains('h2', 'Transacciones creadas').should('be.visible');
    cy.contains('Se crearon').should('contain', '1');
    cy.screenshot('transaction-batch/06-publication-receipt');

    // Stay on the just-published route: Cypress retries retain module variables,
    // whereas the URL is the authoritative batch/receipt context for this attempt.
    cy.reload();
    cy.contains('h2', 'Transacciones creadas').should('be.visible');
  });

  it('persists an existing published counterpart for a manual row and links it only when the batch publishes', () => {
    const description = uniqueName('batch-transfer-outgoing');
    const counterpartDescription = uniqueName('batch-transfer-incoming');
    let counterpartTransactionId: number;

    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('batch-transfer-counterpart-account'),
        institutionName: 'E2E Bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 1000,
        initialBalanceDate: '2026-10-16',
        lastFourDigits: '9876',
        active: true,
      },
    }).then(({ body: counterpartAccount }) =>
      cy
        .authenticatedRequest<{ id: number }>({
          method: 'POST',
          url: '/api/categories',
          body: {
            name: uniqueName('batch-transfer-income-category'),
            categoryType: 'INCOME',
            color: '#16A34A',
            active: true,
          },
        })
        .then(({ body: counterpartCategory }) =>
          cy.authenticatedRequest<{ id: number }>({
            method: 'POST',
            url: '/api/financial-transactions',
            body: {
              transactionDate: '2026-10-16',
              description: counterpartDescription,
              amount: 125.5,
              flow: 'IN',
              origin: 'MANUAL',
              account: { id: counterpartAccount.id },
              category: { id: counterpartCategory.id },
            },
          }),
        )
        .then(({ body: counterpart }) => {
          expect(counterpart.id, 'published counterpart id').to.be.a('number');
          counterpartTransactionId = counterpart.id;
        }),
    );

    createOpenBatch();
    addCompleteManualRow(description);
    cy.contains('[data-cy^="transactionBatchRow-"]', description).within(() => {
      cy.contains('button', 'Editar').click();
    });
    cy.get('[data-cy="transactionBatchInternalTransferToggle"]').check({ force: true });
    cy.get('[data-cy="transactionBatchCounterpartSearch"]').type(counterpartDescription);
    cy.get('[data-cy="transactionBatchTransferSection"]').contains('button', 'Buscar').click();
    cy.get('[data-cy="transactionBatchCounterpartResults"]').contains('button', counterpartDescription).click();
    cy.get('[data-cy="transactionBatchSelectedCounterpart"]')
      .should('contain', counterpartDescription)
      .and('not.contain', String(counterpartTransactionId));
    cy.screenshot('transaction-batch/internal-transfer-selected-counterpart');
    cy.get('.modal').contains('button', 'Cancelar').click();

    cy.reload();
    cy.contains('[data-cy^="transactionBatchRow-"]', description)
      .should('contain', 'Transferencia interna')
      .and('contain', counterpartDescription);

    cy.intercept('POST', '/api/transaction-batches/*/publish').as('publishTransferBatch');
    cy.get('[data-cy="transactionBatchPublish"]').click();
    cy.contains('.modal button', 'Crear transacciones').click();
    cy.wait('@publishTransferBatch').its('response.statusCode').should('eq', 200);
    cy.contains('h2', 'Transacciones creadas').should('be.visible');
    cy.then(() => {
      expect(counterpartTransactionId, 'published counterpart id').to.be.a('number');
      return cy.authenticatedRequest<{
        transferId: number;
        version: number;
        source: { transactionId: number };
        destination: { transactionId: number };
      }>({
        method: 'GET',
        url: `/api/internal-transfers/transactions/${counterpartTransactionId}/product`,
      });
    }).then(({ body: transfer }) => {
      expect(transfer.transferId).to.be.greaterThan(0);
      expect([transfer.source.transactionId, transfer.destination.transactionId]).to.include(counterpartTransactionId);
      expect(
        transfer.source.transactionId === counterpartTransactionId ? transfer.destination.transactionId : transfer.source.transactionId,
      ).to.be.greaterThan(0);
      cy.authenticatedRequest({
        method: 'POST',
        url: `/api/internal-transfers/${transfer.transferId}/unlink`,
        body: { expectedVersion: transfer.version },
      });
      [transfer.source.transactionId, transfer.destination.transactionId].forEach(transactionId => {
        cy.authenticatedRequest({ method: 'DELETE', url: `/api/financial-transactions/${transactionId}` });
      });
    });
  });

  it('appends CSV rows to saved manual work, repairs them, filters locally, and deletes a row', () => {
    const manualDescription = `Manual batch ${Date.now()}`;
    const csvDescription = `CSV batch ${Date.now()}`;

    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.location('pathname').should('match', /\/transaction-batches\/\d+$/);
    cy.location('pathname').then(pathname => {
      batchId = Number(pathname.split('/').pop());
    });

    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('not.exist');
    cy.screenshot('transaction-batch/03-manual-modal-before-save');
    cy.get('#batch-editor-transaction-date').type('2026-10-16');
    cy.get('#batch-editor-description').type(manualDescription);
    cy.get('#batch-editor-amount').type('25.50');
    cy.get('[data-cy="transactionBatchEditorSave"]').click();

    const csv = [csvHeader, `2026-10-17,,${csvDescription},-75.00,MXN,batch-csv-ref,CSV row`].join('\n');
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile(
      {
        contents: Cypress.Buffer.from(csv),
        fileName: 'batch-append.csv',
        mimeType: 'text/csv',
        lastModified: Date.now(),
      },
      { force: true },
    );
    cy.get('[data-cy="transactionBatchAppendCsv"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 2);
    cy.contains('[data-cy^="transactionBatchRow-"]', manualDescription).should('contain', 'Manual');
    cy.contains('[data-cy^="transactionBatchRow-"]', csvDescription).should('contain', 'CSV');
    cy.screenshot('transaction-batch/02-desktop-manual-and-csv');

    cy.viewport(375, 667);
    cy.contains('[data-cy^="transactionBatchRow-"]', manualDescription).find('[data-cy^="transactionBatchMobileDetails-"]').click();
    cy.get('[data-cy^="transactionBatchMobileDetails-"]').should('have.attr', 'aria-expanded', 'true');
    cy.get(`[aria-label="Categoría para ${manualDescription}"]`).should('be.visible');
    cy.screenshot('transaction-batch/04-mobile-expanded-row');
    cy.viewport(1200, 720);

    cy.reload();
    cy.contains('[data-cy^="transactionBatchRow-"]', manualDescription).should('contain', 'Manual');
    cy.contains('[data-cy^="transactionBatchRow-"]', csvDescription).should('contain', 'CSV');

    cy.contains('button', 'Manual').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 1).and('contain', manualDescription);
    cy.contains('button', 'Todos').click();

    cy.on('window:confirm', message => {
      expect(message).to.match(/Eliminar este borrador/);
      return true;
    });
    cy.contains('[data-cy^="transactionBatchRow-"]', csvDescription).within(() => {
      cy.get('[data-cy^="transactionBatchRowActions-"][data-cy$="Toggle"]').click();
      cy.get('[data-cy^="transactionBatchRemove-"]').click();
    });
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 1).and('contain', manualDescription);
  });

  it('persists category and tag changes in sequence and discards another open workspace', () => {
    const description = `Classification batch ${Date.now()}`;

    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.location('pathname').should('match', /\/transaction-batches\/\d+$/);
    cy.location('pathname').then(pathname => {
      batchId = Number(pathname.split('/').pop());
    });
    addCompleteManualRow(description);

    cy.get(`[aria-label="Categoría para ${description}"]`).select(String(categoryId));
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Add"]').click();
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Option"]')
      .contains('button', /batch-tag/)
      .click();
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Chip"]').should('contain', 'batch-tag');
    cy.reload();
    cy.get(`[aria-label="Categoría para ${description}"]`).should('have.value', String(categoryId));
    cy.get('[data-cy^="transactionBatchTags-"] [data-cy$="Chip"]').should('contain', 'batch-tag');

    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.on('window:confirm', message => {
      expect(message).to.match(/Descartar este lote/);
      return true;
    });
    cy.contains('button', 'Descartar lote').click();
    cy.location('pathname').should('eq', '/transaction-batches');
    cy.screenshot('transaction-batch/01-open-batch-list');
  });

  it('groups toolbar actions and keeps sort/count controls readable across responsive review layouts', () => {
    const manualDescription = uniqueName('toolbar-manual');
    const csvDescription = uniqueName('toolbar-csv');

    createOpenBatch();
    addCompleteManualRow(manualDescription);
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile(
      {
        contents: Cypress.Buffer.from([csvHeader, `2026-10-17,,${csvDescription},-40.00,MXN,toolbar,Review controls fixture`].join('\n')),
        fileName: 'toolbar-review-controls.csv',
        mimeType: 'text/csv',
        lastModified: Date.now(),
      },
      { force: true },
    );
    cy.get('[data-cy="transactionBatchAppendCsv"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 2);

    cy.viewport(1280, 900);
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(document.documentElement.clientWidth));
    cy.get('[data-cy="transactionBatchToolbarCreation"]').should('contain', 'Añadir movimiento').and('contain', 'Importar CSV');
    cy.get('[data-cy="transactionBatchToolbarReview"]').should('contain', 'Actualizar').and('contain', 'Reevaluar reglas');
    cy.get('[data-cy="transactionBatchRulesMenu"]').should('have.length', 1);
    cy.get('[data-cy="transactionBatchToolbarDestructive"]').should('contain', 'Descartar lote');
    cy.screenshot('transaction-batch/17-toolbar-wide', { capture: 'viewport' });

    cy.get('[data-cy="transactionBatchRulesMenu"]').click();
    cy.get('[data-cy="transactionBatchRulesLanding"]').should('contain', 'Ver reglas');
    cy.contains('[data-cy="transactionBatchToolbarRules"]', 'Crear regla de descripción').should('be.visible');
    cy.contains('[data-cy="transactionBatchToolbarRules"]', 'Crear regla de clasificación').should('be.visible');
    cy.screenshot('transaction-batch/18-rules-menu', { capture: 'viewport' });
    cy.get('[data-cy="transactionBatchRulesMenu"]').click();
    cy.get('[data-cy="transactionBatchRulesLanding"]').should('not.be.visible');

    cy.get('[data-cy="transactionBatchReviewControls"]').within(() => {
      cy.contains('Ordenar por').should('be.visible');
      cy.get('[data-cy="transactionBatchVisibleCount"]').should('have.text', 'Mostrando 2 de 2 borradores');
      cy.get('[data-cy="transactionBatchSort"]').select('Fecha');
      cy.get('[data-cy="transactionBatchSortDirection"]').should('have.attr', 'aria-label', 'Ascendente').click();
      cy.get('[data-cy="transactionBatchSortDirection"]').should('have.attr', 'aria-label', 'Descendente');
      cy.get('[data-cy="transactionBatchVisibleCount"]').should('have.text', 'Mostrando 2 de 2 borradores');
    });
    cy.screenshot('transaction-batch/19-review-all', { capture: 'viewport' });

    cy.get('[data-cy="transactionBatchFilterCSV"]').click();
    cy.get('[data-cy="transactionBatchVisibleCount"]').should('have.text', 'Mostrando 1 de 2 borradores');
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 1).and('contain', csvDescription);
    cy.screenshot('transaction-batch/20-review-filtered', { capture: 'viewport' });

    cy.viewport(1024, 768);
    cy.get('[data-cy="transactionBatchToolbar"]').should('be.visible');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(document.documentElement.clientWidth));
    cy.screenshot('transaction-batch/21-toolbar-laptop', { capture: 'viewport' });

    cy.viewport(375, 667);
    cy.get('[data-cy="transactionBatchToolbar"]').should('be.visible');
    cy.document().then(document => expect(document.documentElement.scrollWidth).to.be.lte(document.documentElement.clientWidth));
    cy.screenshot('transaction-batch/22-toolbar-mobile', { capture: 'viewport' });
    cy.get('[data-cy="transactionBatchQueue"]').scrollIntoView();
    cy.get('[data-cy="transactionBatchReviewControls"]').within(() => {
      cy.contains('Ordenar por').should('be.visible');
      cy.get('[data-cy="transactionBatchVisibleCount"]').should('be.visible').and('have.text', 'Mostrando 1 de 2 borradores');
    });
    cy.get('[data-cy="transactionBatchReviewControls"]').then($controls => {
      const sortControl = $controls.find('[data-cy="transactionBatchSort"]')[0].getBoundingClientRect();
      const visibleCount = $controls.find('[data-cy="transactionBatchVisibleCount"]')[0].getBoundingClientRect();
      const reviewMeta = $controls.find('.transaction-batch-review-meta')[0];
      const reviewControls = $controls[0];
      const metaStyle = window.getComputedStyle(reviewMeta);
      const controlsStyle = window.getComputedStyle(reviewControls);
      expect(visibleCount.top).to.be.greaterThan(sortControl.bottom - 1);
      expect(
        visibleCount.left,
        `mobile review layout: meta=${metaStyle.display}/${metaStyle.flexDirection}/${metaStyle.width}, controls=${controlsStyle.display}/${controlsStyle.width}`,
      ).to.be.lessThan(sortControl.right);
    });
    cy.screenshot('transaction-batch/23-review-mobile', { capture: 'viewport' });
  });

  it('applies CSV normalization before classification and protects an explicit clear on reevaluation', () => {
    const token = uniqueName('batch-auto-apply');
    const normalizedDescription = `Normalizado ${token}`;
    createAutoApplyRules(token, normalizedDescription).then(({ descriptionRule, classificationRule }) => {
      createOpenBatch();

      cy.get('[data-cy="transactionBatchRulesMenu"]').click();
      cy.get('[data-cy="transactionBatchSettings"]').click();
      cy.get('#batch-setting-autoApplyDescriptions').check();
      cy.get('#batch-setting-autoApplyCategories').check();
      cy.get('#batch-setting-autoApplyTags').check();
      cy.intercept('PUT', '/api/transaction-batches/*/settings').as('saveAutoApplySettings');
      cy.contains('.modal button', 'Guardar y reevaluar').click();
      cy.wait('@saveAutoApplySettings').then(({ request, response }) => {
        expect(request.body).to.include({
          autoApplyDescriptions: true,
          autoApplyCategories: true,
          autoApplyTags: true,
          protectManualChanges: true,
        });
        expect(response?.statusCode, JSON.stringify(response?.body)).to.eq(200);
      });
      cy.get('.modal').should('not.exist');

      const csv = [csvHeader, `2026-10-17,,${token},-75.00,MXN,batch-auto-apply,CSV row`].join('\n');
      cy.get('[data-cy="transactionBatchCsvFile"]').selectFile(
        {
          contents: Cypress.Buffer.from(csv),
          fileName: 'batch-auto-apply.csv',
          mimeType: 'text/csv',
          lastModified: Date.now(),
        },
        { force: true },
      );
      cy.get('[data-cy="transactionBatchAppendCsv"]').click();
      cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription).should('contain', 'CSV');
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', String(categoryId));
      cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('contain', tagName);
      cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription).within(() => {
        cy.get('[data-testid="transactionBatchAppliedRuleAttribution"]')
          .should('have.length', 3)
          .and('contain', descriptionRule.name)
          .and('contain', classificationRule.name);
        cy.get('[data-testid="transactionBatchAppliedRuleAttribution"]').first().find('[data-cy$="Toggle"]').click();
        cy.get('[data-testid="transactionBatchAppliedRuleAttribution"]')
          .first()
          .find('[data-cy$="Menu"]')
          .should('contain', descriptionRule.name)
          .find('a')
          .should('have.attr', 'href', `/description-normalization-rule/${descriptionRule.id}/view`);
        cy.contains('[data-testid="transactionBatchAppliedRuleAttribution"]', descriptionRule.name)
          .find('a')
          .should('have.attr', 'href', `/description-normalization-rule/${descriptionRule.id}/view`);
        cy.contains('[data-testid="transactionBatchAppliedRuleAttribution"]', classificationRule.name)
          .first()
          .find('a')
          .should('have.attr', 'href', `/transaction-rule/${classificationRule.id}/view`);
      });
      cy.screenshot('transaction-batch/13-compact-attribution-actions');
      cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription)
        .invoke('attr', 'data-cy')
        .then(rowDataCy => {
          openRowActions(`[data-cy="${rowDataCy}"]`);
          cy.screenshot('transaction-batch/16-kebab-menu');
        });

      cy.reload();
      cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription).within(() => {
        cy.get('[data-testid="transactionBatchAppliedRuleAttribution"]')
          .should('have.length', 3)
          .and('contain', descriptionRule.name)
          .and('contain', classificationRule.name);
      });

      // Persist an explicit manual category/tag fixture. Auto-application is intentionally
      // preview state after reload, whereas the following clear must prove durable manual
      // suppression through the actual row controls.
      let candidateId: number;
      let candidateVersion: number;
      cy.then(() => {
        expect(batchId).to.be.a('number');
        return cy.authenticatedRequest<{ candidates: Array<{ id: number; version: number; description: string }> }>({
          method: 'GET',
          url: `/api/transaction-batches/${batchId}`,
        });
      })
        .then(({ body }) => {
          const candidate = body.candidates.find(current => current.description === normalizedDescription);
          if (!candidate) throw new Error('Normalized CSV candidate was not returned by the batch read model');
          candidateId = candidate.id;
          candidateVersion = candidate.version;
          return cy.authenticatedRequest<{ candidate: { version: number } }>({
            method: 'PUT',
            url: `/api/transaction-batches/${batchId}/candidates/${candidateId}/category`,
            body: { expectedCandidateVersion: candidateVersion, categoryId },
          });
        })
        .then(({ body }) => {
          candidateVersion = body.candidate.version;
          return cy.authenticatedRequest<{ candidate: { version: number } }>({
            method: 'PUT',
            url: `/api/transaction-batches/${batchId}/candidates/${candidateId}/tags`,
            body: { expectedCandidateVersion: candidateVersion, tagIds: [tagId] },
          });
        })
        .then(({ body }) => {
          expect(body.candidate.version).to.be.greaterThan(candidateVersion);
        });
      cy.reload();
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', String(categoryId));
      cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('contain', tagName);
      cy.intercept('PUT', '/api/transaction-batches/*/candidates/*/category').as('persistCategoryClear');
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).select('Sin categoría');
      cy.wait('@persistCategoryClear').then(({ request, response }) => {
        expect(request.url).to.include('/category');
        expect(response?.statusCode).to.eq(200);
      });
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', '');
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('not.be.disabled');
      cy.intercept('PUT', '/api/transaction-batches/*/candidates/*/tags').as('persistTagClear');
      cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Remove"]').click();
      cy.wait('@persistTagClear').then(({ request, response }) => {
        expect(request.url).to.include('/tags');
        expect(response?.statusCode).to.eq(200);
      });
      cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('not.exist');
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('not.be.disabled');
      cy.get('[data-cy="transactionBatchReevaluate"]').click();
      cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', '');
      cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('not.exist');
      cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription)
        .find('[data-testid="transactionBatchAppliedRuleAttribution"]')
        .should('have.length', 1)
        .and('contain', descriptionRule.name);
      cy.screenshot('transaction-batch/07-rule-protection');

      cy.authenticatedRequest({ method: 'DELETE', url: `/api/description-normalization-rules/${descriptionRule.id}` })
        .its('status')
        .should('eq', 204);
      cy.authenticatedRequest({ method: 'DELETE', url: `/api/transaction-rules/${classificationRule.id}` })
        .its('status')
        .should('eq', 204);
      cy.reload();
      cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription).within(() => {
        cy.contains('[data-testid="transactionBatchAppliedRuleAttribution"]', descriptionRule.name).find('a').should('not.exist');
        // The explicit manual clear has already suppressed classification attribution; deleting
        // its rule cannot make an attribution reappear.
        cy.contains('[data-testid="transactionBatchAppliedRuleAttribution"]', classificationRule.name).should('not.exist');
      });
    });
  });

  it('keeps an in-progress edit when an external candidate version wins', () => {
    const description = `External conflict ${Date.now()}`;
    let externalCandidate: { id: number; version: number };
    createOpenBatch();
    cy.intercept('POST', '/api/transaction-batches/*/candidates').as('initialManualSave');
    addCompleteManualRow(description);
    cy.wait('@initialManualSave').its('response.statusCode').should('eq', 200);

    cy.then(() => {
      expect(batchId).to.be.a('number');
      return cy.authenticatedRequest<{ candidates: Array<{ id: number; version: number }> }>({
        method: 'GET',
        url: `/api/transaction-batches/${batchId}`,
      });
    }).then(({ body }) => {
      externalCandidate = body.candidates[0];
    });
    cy.then(() =>
      cy.authenticatedRequest({
        method: 'PATCH',
        url: `/api/transaction-batches/${batchId}/candidates/${externalCandidate.id}`,
        body: { expectedCandidateVersion: externalCandidate.version, notes: 'External saved note' },
      }),
    )
      .its('status')
      .should('eq', 200);

    cy.contains('[data-cy^="transactionBatchRow-"]', description).within(() => {
      cy.contains('button', 'Editar').click();
    });
    cy.get('summary').contains('Más detalles').click();
    cy.get('#batch-editor-notes').clear();
    cy.get('#batch-editor-notes').type('Mi nota local no se pierde');
    cy.get('[data-cy="transactionBatchEditorSave"]').click();
    cy.get('[role="alert"]').should('contain', 'Esta fila cambió en otra sesión. Conservamos tu edición local');
    cy.get('#batch-editor-notes').should('have.value', 'Mi nota local no se pierde');
    cy.screenshot('transaction-batch/08-version-conflict');
  });

  it('authors contextual rules from the batch and rows without duplicating a saved rule after a reevaluation failure', () => {
    const manualDescription = uniqueName('manual-context');
    const csvSourceDescription = uniqueName('csv-source');
    const normalizedDescription = `Normalized ${csvSourceDescription}`;
    const csvRuleName = uniqueName('csv-normalization-rule');
    const partialRuleName = uniqueName('partial-normalization-rule');
    createOpenBatch();
    addCompleteManualRow(manualDescription);
    // batchId is assigned by the queued create flow; keep the interceptor independent of that later callback.
    cy.intercept('PUT', '/api/transaction-batches/*/candidates/*/category').as('manualContextCategorySaved');
    cy.intercept('GET', '/api/transaction-batches/*').as('batchAfterManualContextCategory');
    cy.get(`[aria-label="Categoría para ${manualDescription}"]`).select(String(categoryId));
    cy.wait('@manualContextCategorySaved').its('response.statusCode').should('eq', 200);
    cy.wait('@batchAfterManualContextCategory');

    const csv = [csvHeader, `2026-10-17,,${csvSourceDescription},-75.00,MXN,contextual-csv,CSV contextual row`].join('\n');
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile(
      {
        contents: Cypress.Buffer.from(csv),
        fileName: 'contextual-rules.csv',
        mimeType: 'text/csv',
        lastModified: Date.now(),
      },
      { force: true },
    );
    cy.get('[data-cy="transactionBatchAppendCsv"]').click();
    cy.contains('[data-cy^="transactionBatchRow-"]', csvSourceDescription).should('be.visible');

    cy.then(() =>
      cy.authenticatedRequest<{ candidates: Array<{ id: number; description: string; sourceDescription?: string | null }> }>({
        method: 'GET',
        url: `/api/transaction-batches/${batchId}`,
      }),
    ).then(({ body }) => {
      const csvCandidate = body.candidates.find(candidate => candidate.sourceDescription === csvSourceDescription);
      const manualCandidate = body.candidates.find(candidate => candidate.description === manualDescription);
      if (!csvCandidate) throw new Error('Expected a CSV candidate with its immutable source baseline');
      if (!manualCandidate) throw new Error('Expected the completed manual candidate');
      const csvCandidateId = csvCandidate.id;
      const manualCandidateId = manualCandidate.id;

      cy.intercept('POST', '/api/description-normalization-rules/configured').as('contextualCsvDnrCreate');
      openRowActions(`[data-cy="transactionBatchRow-${csvCandidateId}"]`);
      cy.get(`[data-cy="transactionBatchRow-${csvCandidateId}"]`).within(() => {
        cy.get(`[data-cy="transactionBatchCreateNormalizationRule-${csvCandidateId}"]`).click();
      });
      cy.get('#contextual-description-rule-name').clear();
      cy.get('#contextual-description-rule-name').type(csvRuleName);
      cy.get('#contextual-description-rule-condition-value-0').should('have.value', csvSourceDescription);
      cy.get('#contextual-description-rule-result').should('have.value', csvSourceDescription);
      cy.screenshot('transaction-batch/09-contextual-dnr-csv');
      cy.get('[data-cy="batchContextualRuleSave"]').click();
      cy.wait('@contextualCsvDnrCreate').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        const id = response?.body.id as number;
        createdDescriptionRuleIds.push(id);
        cy.authenticatedRequest({ method: 'GET', url: `/api/description-normalization-rules/${id}` })
          .its('status')
          .should('eq', 200);
      });
      cy.get('[data-cy="transactionBatchRuleCreationFeedback"]').should('contain', 'se guardó');

      cy.intercept('POST', '/api/transaction-rules/configured').as('contextualTransactionRuleCreate');
      cy.intercept('POST', '/api/transaction-batches/*/candidates/*/reevaluate').as('contextualRowReevaluation');
      cy.contains('[data-cy^="transactionBatchRow-"]', manualDescription).as('manualContextRow');
      openRowActions(`[data-cy="transactionBatchRow-${manualCandidateId}"]`);
      cy.get('@manualContextRow').within(() => {
        cy.get('[data-cy^="transactionBatchCreateTransactionRule-"]').click();
      });
      cy.get('#contextual-transaction-rule-name').should('have.value', `Regla para ${manualDescription}`);
      cy.get('[data-cy="conditionRow"]').should('have.length', 2);
      cy.get('[data-cy="conditionRow"]').eq(0).should('have.attr', 'data-condition-field', 'DESCRIPTION');
      cy.get('[data-cy="conditionRow"]').eq(1).should('have.attr', 'data-condition-field', 'FLOW');
      cy.get('[data-cy="transactionRuleCategoryIdentity"]').should('contain', 'batch-category');
      cy.contains(/cuenta se muestra sólo como contexto/i).should('be.visible');
      cy.screenshot('transaction-batch/10-contextual-transaction-rule');
      cy.get('[data-cy="batchContextualRuleSaveAndReevaluateRow"]').click();
      cy.wait('@contextualTransactionRuleCreate').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        const id = response?.body.id as number;
        createdTransactionRuleIds.push(id);
        cy.authenticatedRequest({ method: 'GET', url: `/api/transaction-rules/${id}` })
          .its('status')
          .should('eq', 200);
      });
      cy.wait('@contextualRowReevaluation').then(({ request, response }) => {
        expect(request.url).to.contain(`/candidates/${manualCandidateId}/reevaluate`);
        expect(response?.statusCode).to.equal(200);
      });
      cy.get('[data-cy="transactionBatchRuleCreationFeedback"]').should('contain', 'sólo esta fila');

      cy.viewport(375, 667);
      cy.get(`[data-cy="transactionBatchMobileDetails-${csvCandidateId}"]`).scrollIntoView();
      cy.get(`[data-cy="transactionBatchMobileDetails-${csvCandidateId}"]`).click();
      openRowActions(`[data-cy="transactionBatchRow-${csvCandidateId}"]`);
      cy.get(`[data-cy="transactionBatchRow-${csvCandidateId}"]`).within(() => {
        cy.get(`[data-cy="transactionBatchCreateTransactionRule-${csvCandidateId}"]`).click();
      });
      cy.get('[data-cy="transactionBatchRuleCreationModal"]').should('be.visible');
      cy.screenshot('transaction-batch/11-contextual-rule-mobile');
      cy.get('.modal').contains('button', 'Cancelar').click();
      cy.viewport(1200, 720);

      cy.intercept('POST', '/api/description-normalization-rules/configured').as('partialRuleCreate');
      let batchReevaluationAttempts = 0;
      cy.intercept('POST', '/api/transaction-batches/*/reevaluate', request => {
        batchReevaluationAttempts += 1;
        if (batchReevaluationAttempts === 1) {
          request.reply({ statusCode: 503, body: { detail: 'Controlled reevaluation outage' } });
        }
      }).as('controlledBatchReevaluation');
      openRowActions(`[data-cy="transactionBatchRow-${csvCandidateId}"]`);
      cy.get(`[data-cy="transactionBatchRow-${csvCandidateId}"]`).within(() => {
        cy.get(`[data-cy="transactionBatchCreateNormalizationRule-${csvCandidateId}"]`).click();
      });
      cy.get('#contextual-description-rule-name').clear();
      cy.get('#contextual-description-rule-name').type(partialRuleName);
      cy.get('#contextual-description-rule-result').clear();
      cy.get('#contextual-description-rule-result').type(normalizedDescription);
      cy.get('[data-cy="batchContextualRuleSaveAndReevaluateBatch"]').click();
      cy.wait('@partialRuleCreate').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        const id = response?.body.id as number;
        createdDescriptionRuleIds.push(id);
        cy.authenticatedRequest({ method: 'GET', url: `/api/description-normalization-rules/${id}` })
          .its('status')
          .should('eq', 200);
      });
      cy.wait('@controlledBatchReevaluation').its('response.statusCode').should('eq', 503);
      cy.get('[data-cy="batchContextualRulePartialSuccess"]').should('contain', 'se creó correctamente');
      cy.screenshot('transaction-batch/12-contextual-rule-partial-success');
      cy.get('[data-cy="batchContextualRuleRetryReevaluation"]').click();
      cy.wait('@controlledBatchReevaluation').its('response.statusCode').should('eq', 200);
      cy.get('@partialRuleCreate.all').should('have.length', 1);
    });
  });

  it('previews and applies classification for a materialized manual creation before final Save', () => {
    const description = uniqueName('manual-preview');
    createManualClassificationRule(description);
    createOpenBatch();

    cy.intercept('POST', '/api/transaction-batches/*/candidates').as('materializeManualCandidate');
    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('not.exist');
    cy.get('#batch-editor-transaction-date').type('2026-10-16');
    cy.get('#batch-editor-description').type(description);
    cy.get('#batch-editor-amount').type('55.00');
    cy.wait('@materializeManualCandidate').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy^="transactionBatchRow-"]').should('not.exist');
    cy.get('.modal')
      .contains(/categoría sugerida/i)
      .should('be.visible');
    cy.get('.modal').should('not.contain', 'descripción sugerida');
    cy.intercept('POST', '/api/transaction-batches/*/candidates/*/apply-rules').as('applyManualSuggestion');
    cy.contains('.modal button', /aplicar categoría sugerida/i).click();
    cy.wait('@applyManualSuggestion').its('response.statusCode').should('eq', 200);
    cy.get('.modal').should('not.contain', /categoría sugerida/i);
    cy.contains('.modal button', /aplicar etiquetas sugeridas/i).click();
    cy.wait('@applyManualSuggestion').its('response.statusCode').should('eq', 200);
    cy.get('.modal').should('not.contain', /etiquetas sugeridas/i);
    cy.get('[data-cy="transactionBatchEditorSave"]').click();

    cy.contains('[data-cy^="transactionBatchRow-"]', description)
      .should('be.visible')
      .within(() => {
        cy.get('[aria-label^="Categoría para"]').should('have.value', String(categoryId));
        cy.get('[data-cy$="Chip"]').should('contain', tagName);
      });
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 1);
  });

  it('removes a provisional manual candidate on Cancel and leaves an existing-row cancel untouched', () => {
    const provisionalDescription = uniqueName('manual-cancel');
    const existingDescription = uniqueName('manual-existing');
    createOpenBatch();

    cy.intercept('POST', '/api/transaction-batches/*/candidates').as('materializeForCancel');
    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('#batch-editor-description').type(provisionalDescription);
    cy.get('#batch-editor-amount').type('19.00');
    cy.wait('@materializeForCancel').its('response.statusCode').should('eq', 200);
    cy.then(() =>
      cy.authenticatedRequest<{ candidates: Array<{ description?: string | null }> }>({
        method: 'GET',
        url: `/api/transaction-batches/${batchId}`,
      }),
    ).then(({ body }) => {
      expect(body.candidates.filter(candidate => candidate.description === provisionalDescription)).to.have.length(1);
    });
    cy.intercept('DELETE', '/api/transaction-batches/*/candidates/*').as('removeProvisionalForCancel');
    cy.get('.modal').contains('button', 'Cancelar').click();
    cy.wait('@removeProvisionalForCancel').its('response.statusCode').should('eq', 204);
    cy.get('[data-cy^="transactionBatchRow-"]').should('not.exist');
    cy.reload();
    cy.get('[data-cy^="transactionBatchRow-"]').should('not.exist');
    cy.then(() =>
      cy.authenticatedRequest<{ candidates: Array<{ description?: string | null }> }>({
        method: 'GET',
        url: `/api/transaction-batches/${batchId}`,
      }),
    ).then(({ body }) => {
      expect(body.candidates.filter(candidate => candidate.description === provisionalDescription)).to.have.length(0);
    });

    addCompleteManualRow(existingDescription, '29.00');
    cy.contains('[data-cy^="transactionBatchRow-"]', existingDescription).as('existingRow');
    cy.get('@existingRow').within(() => {
      cy.contains('button', 'Editar').click();
    });
    cy.get('#batch-editor-description').clear();
    cy.get('#batch-editor-description').type(`${existingDescription} local`);
    cy.get('.modal').contains('button', 'Cancelar').click();
    cy.contains('[data-cy^="transactionBatchRow-"]', existingDescription).should('be.visible');
    cy.contains('[data-cy^="transactionBatchRow-"]', `${existingDescription} local`).should('not.exist');
  });

  it('observes bounded product loading for comparable 40 and 500 row CSV workspaces', () => {
    const token = uniqueName('batch-metric');
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/transaction-rules/configured',
      body: {
        name: uniqueName('batch-metric-tag-rule'),
        description: 'Batch measurement tag fixture',
        active: true,
        conditionLogic: 'ALL',
        resultingCategory: null,
        resultingTags: [{ id: tagId }],
        conditions: [
          { field: 'DESCRIPTION', operator: 'CONTAINS', value: token, secondValue: null, caseSensitive: false },
          { field: 'FLOW', operator: 'EQUALS', value: 'OUT', secondValue: null, caseSensitive: false },
        ],
      },
    }).then(({ body }) => {
      createdTransactionRuleIds.push(body.id);
    });

    measureComparableCsvBatch(40, token);
    measureComparableCsvBatch(500, token);
  });
});
