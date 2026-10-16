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
    cy.location('pathname').then(pathname => {
      batchId = Number(pathname.split('/').pop());
    });
  };

  const addCompleteManualRow = (description: string, amount = '-125.50') => {
    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').within(() => {
      cy.contains('button', 'Editar').click();
    });
    cy.get('#batch-row-date').type('2026-10-16');
    cy.get('#batch-row-description').type(description);
    cy.get('#batch-row-amount').type(amount);
    cy.contains('.modal button', 'Guardar').click();
  };

  const createAutoApplyRules = (token: string, normalizedDescription: string) => {
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/description-normalization-rules/configured',
      body: {
        name: uniqueName('batch-normalization-rule'),
        active: true,
        conditionOperator: 'ALL',
        resultingDescription: normalizedDescription,
        conditions: [{ operator: 'CONTAINS', value: token, caseSensitive: false, position: 0 }],
      },
    }).then(({ body }) => {
      createdDescriptionRuleIds.push(body.id);
    });
    cy.authenticatedRequest<{ id: number }>({
      method: 'POST',
      url: '/api/transaction-rules/configured',
      body: {
        name: uniqueName('batch-classification-rule'),
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
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile({
      contents: Cypress.Buffer.from([csvHeader, ...csvRows].join('\n')),
      fileName: `batch-metric-${rowCount}.csv`,
      mimeType: 'text/csv',
      lastModified: Date.now(),
    });
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
    cy.get('[data-cy^="transactionBatchRow-"]')
      .should('have.length', 1)
      .within(() => {
        cy.contains('button', 'Editar').click();
      });
    cy.get('#batch-row-date').type('2026-10-16');
    cy.get('#batch-row-description').type('Movimiento de lote E2E');
    cy.get('#batch-row-amount').type('-125.50');
    cy.contains('.modal button', 'Guardar').click();

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

  it('appends CSV rows to saved manual work, repairs them, filters locally, and deletes a row', () => {
    const manualDescription = `Manual batch ${Date.now()}`;
    const csvDescription = `CSV batch ${Date.now()}`;

    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.location('pathname').then(pathname => {
      batchId = Number(pathname.split('/').pop());
    });

    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 1).and('contain', 'Pendiente');
    cy.screenshot('transaction-batch/03-repairable-row');
    cy.get('[data-cy^="transactionBatchRow-"]')
      .should('have.length', 1)
      .within(() => {
        cy.contains('button', 'Editar').click();
      });
    cy.get('#batch-row-date').type('2026-10-16');
    cy.get('#batch-row-description').type(manualDescription);
    cy.get('#batch-row-amount').type('-25.50');
    cy.contains('.modal button', 'Guardar').click();

    const csv = [csvHeader, `2026-10-17,,${csvDescription},-75.00,MXN,batch-csv-ref,CSV row`].join('\n');
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile({
      contents: Cypress.Buffer.from(csv),
      fileName: 'batch-append.csv',
      mimeType: 'text/csv',
      lastModified: Date.now(),
    });
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
      cy.contains('button', 'Eliminar').click();
    });
    cy.get('[data-cy^="transactionBatchRow-"]').should('have.length', 1).and('contain', manualDescription);
  });

  it('persists category and tag changes in sequence and discards another open workspace', () => {
    const description = `Classification batch ${Date.now()}`;

    cy.visit('/transaction-batches/new');
    cy.get('[data-cy="transactionBatchAccount"]').select(String(accountId));
    cy.get('[data-cy="transactionBatchCreateSubmit"]').click();
    cy.location('pathname').then(pathname => {
      batchId = Number(pathname.split('/').pop());
    });
    cy.get('[data-cy="transactionBatchAddManual"]').click();
    cy.get('[data-cy^="transactionBatchRow-"]').within(() => {
      cy.contains('button', 'Editar').click();
    });
    cy.get('#batch-row-date').type('2026-10-16');
    cy.get('#batch-row-description').type(description);
    cy.get('#batch-row-amount').type('-125.50');
    cy.contains('.modal button', 'Guardar').click();

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

  it('applies CSV normalization before classification and protects an explicit clear on reevaluation', () => {
    const token = uniqueName('batch-auto-apply');
    const normalizedDescription = `Normalizado ${token}`;
    createAutoApplyRules(token, normalizedDescription);
    createOpenBatch();

    cy.get('[data-cy="transactionBatchSettings"]').click();
    cy.get('#batch-setting-autoApplyDescriptions').check();
    cy.get('#batch-setting-autoApplyCategories').check();
    cy.get('#batch-setting-autoApplyTags').check();
    cy.contains('.modal button', 'Guardar y reevaluar').click();

    const csv = [csvHeader, `2026-10-17,,${token},-75.00,MXN,batch-auto-apply,CSV row`].join('\n');
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile({
      contents: Cypress.Buffer.from(csv),
      fileName: 'batch-auto-apply.csv',
      mimeType: 'text/csv',
      lastModified: Date.now(),
    });
    cy.get('[data-cy="transactionBatchAppendCsv"]').click();
    cy.contains('[data-cy^="transactionBatchRow-"]', normalizedDescription).should('contain', 'CSV');
    cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', String(categoryId));
    cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('contain', tagName);

    cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).select('');
    cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', '');
    cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Remove"]').click();
    cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('not.exist');

    cy.get('[data-cy="transactionBatchReevaluate"]').click();
    cy.get(`[aria-label="Categoría para ${normalizedDescription}"]`).should('have.value', '');
    cy.get('[data-cy^="transactionBatchTags-"][data-cy$="Chip"]').should('not.exist');
    cy.screenshot('transaction-batch/07-rule-protection');
  });

  it('keeps an in-progress edit when an external candidate version wins', () => {
    const description = `External conflict ${Date.now()}`;
    let externalCandidate: { id: number; version: number };
    createOpenBatch();
    cy.intercept('PATCH', '/api/transaction-batches/*/candidates/*').as('initialManualSave');
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
    cy.get('#batch-row-notes').clear();
    cy.get('#batch-row-notes').type('Mi nota local no se pierde');
    cy.contains('.modal button', 'Guardar').click();
    cy.get('[role="alert"]').should('contain', 'No se pudo guardar la fila. No se reemplazó tu edición local.');
    cy.get('#batch-row-notes').should('have.value', 'Mi nota local no se pierde');
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
    cy.get('[data-cy="transactionBatchCsvFile"]').selectFile({
      contents: Cypress.Buffer.from(csv),
      fileName: 'contextual-rules.csv',
      mimeType: 'text/csv',
      lastModified: Date.now(),
    });
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
      cy.get(`[data-cy="transactionBatchCreateNormalizationRule-${csvCandidateId}"]`).click();
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
      cy.get('@manualContextRow').find('[data-cy^="transactionBatchCreateTransactionRule-"]').click();
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
      cy.get(`[data-cy="transactionBatchCreateTransactionRule-${csvCandidateId}"]`).click();
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
      cy.get(`[data-cy="transactionBatchCreateNormalizationRule-${csvCandidateId}"]`).click();
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
