describe('TransactionIngestion CSV workflow e2e test', () => {
  const username = Cypress.env('INGESTION_E2E_USERNAME') ?? 'cypress_ingestion';
  const password = Cypress.env('INGESTION_E2E_PASSWORD') ?? 'cypress_ingestion';

  interface E2EEntity {
    id: number;
    name?: string;
    [key: string]: unknown;
  }

  let account: E2EEntity | undefined;
  let expenseCategory: E2EEntity | undefined;
  let incomeCategory: E2EEntity | undefined;
  let tag: E2EEntity | undefined;
  let rule: E2EEntity | undefined;
  let transactionIngestionId: number | undefined;
  let scenarioToken: string;
  let outDescription: string;
  let inDescription: string;

  const header = 'transactionDate,postingDate,description,signedAmount,currency,externalReference,notes';
  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const uploadCsvFromCreatePage = (csv: string, filename: string, expectedStatusCode = 200) => {
    cy.intercept('GET', '/api/financial-accounts+(?*|)').as('accountsRequest');
    cy.intercept('POST', '/api/transaction-ingestions/file').as('createWorkflowRequest');
    cy.intercept('GET', '/api/transaction-ingestions/*/workflow').as('workflowRequest');
    cy.visit('/transaction-ingestion/new');
    cy.wait('@accountsRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="account"]').select(account?.name as string);
    cy.get('[data-cy="ingestionType"]').select('FILE');
    cy.get('[data-cy="csvFile"]').selectFile({
      contents: Cypress.Buffer.from(csv),
      fileName: filename,
      mimeType: 'text/csv',
      lastModified: Date.now(),
    });
    cy.get('[data-cy="entityCreateSaveButton"]').click();
    cy.wait('@createWorkflowRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(expectedStatusCode);
      if (expectedStatusCode === 200) {
        transactionIngestionId = response?.body.transactionIngestionId;
        expect(transactionIngestionId).to.be.a('number');
      }
    });
  };

  beforeEach(() => {
    cy.login(username, password);
    scenarioToken = uniqueName('csv-e2e-rideflow');
    outDescription = `${scenarioToken} trip`;
    inDescription = `${scenarioToken} refund`;

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('csv-e2e-account'),
        institutionName: 'E2E Bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-01-01',
        active: true,
      },
    }).then(({ body }) => {
      account = body;
    });

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/categories',
      body: {
        name: uniqueName('Transporte'),
        description: 'CSV ingestion E2E expense category',
        categoryType: 'EXPENSE',
        color: '#336699',
        active: true,
      },
    }).then(({ body }) => {
      expenseCategory = body;
    });

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/categories',
      body: {
        name: uniqueName('Salary'),
        description: 'CSV ingestion E2E income category',
        categoryType: 'INCOME',
        color: '#669933',
        active: true,
      },
    }).then(({ body }) => {
      incomeCategory = body;
    });

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/tags',
      body: {
        name: uniqueName('Ride share'),
        description: 'CSV ingestion E2E tag',
        color: '#993366',
        active: true,
      },
    }).then(({ body }) => {
      tag = body;
    });

    cy.then(() => {
      expect(expenseCategory?.id).to.be.a('number');
      expect(tag?.id).to.be.a('number');

      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-rules/configured',
        body: {
          name: uniqueName('Uber gastos'),
          description: 'CSV ingestion E2E expense rule',
          conditionLogic: 'ALL',
          active: true,
          resultingCategory: { id: expenseCategory?.id },
          resultingTags: [{ id: tag?.id }],
          conditions: [
            {
              field: 'DESCRIPTION',
              operator: 'CONTAINS',
              value: scenarioToken,
              secondValue: null,
              caseSensitive: false,
            },
            {
              field: 'FLOW',
              operator: 'EQUALS',
              value: 'OUT',
              secondValue: null,
              caseSensitive: false,
            },
          ],
        },
      }).then(({ body }) => {
        rule = body;
      });
    });
  });

  afterEach(() => {
    if (transactionIngestionId) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-ingestions/${transactionIngestionId}`,
        failOnStatusCode: false,
      });
      transactionIngestionId = undefined;
    }

    if (rule?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${rule.id}`,
        failOnStatusCode: false,
      });
      rule = undefined;
    }

    if (tag?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/tags/${tag.id}`,
        failOnStatusCode: false,
      });
      tag = undefined;
    }

    [expenseCategory, incomeCategory].forEach(category => {
      if (category?.id) {
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/categories/${category.id}`,
          failOnStatusCode: false,
        });
      }
    });
    expenseCategory = undefined;
    incomeCategory = undefined;

    if (account?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${account.id}`,
        failOnStatusCode: false,
      });
      account = undefined;
    }
  });

  it('classifies CSV expense suggestions by row flow and imports only valid category/tag selections', () => {
    cy.then(() => {
      expect(account?.name).to.be.a('string');
      expect(expenseCategory?.id).to.be.a('number');
      expect(tag?.id).to.be.a('number');
      expect(rule?.id).to.be.a('number');
    });

    const csv = [
      header,
      `2026-01-16,,${outDescription},-100.00,MXN,${scenarioToken}-trip,e2e out row`,
      `2026-01-17,,${inDescription},100.00,MXN,${scenarioToken}-refund,e2e in row`,
    ].join('\n');

    let oldClassificationPreviewCalled = false;
    let financialTransactionRulePreviewCalled = false;
    cy.intercept('POST', '/api/transaction-ingestions/*/classification-preview', req => {
      oldClassificationPreviewCalled = true;
      req.continue();
    });
    cy.intercept('POST', '/api/financial-transactions/rule-preview', req => {
      financialTransactionRulePreviewCalled = true;
      req.continue();
    });
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/prepare').as('prepareCandidatesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/apply-rules').as('applyCandidateRulesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/*/confirm-no-suggestions').as('confirmNoSuggestionsRequest');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');

    uploadCsvFromCreatePage(csv, 'uber-flow-classification.csv');

    cy.url().should('match', /\/transaction-ingestion\/\d+$/);
    cy.get('[data-cy="workflowReviewHeading"]').should('exist');
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="workflowRows"]').should('contain', outDescription).and('contain', inDescription);
    cy.wait('@prepareCandidatesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    let outCandidateId: number;
    let inCandidateId: number;
    cy.wait('@candidateRulePreviewRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const rows = response?.body.rows ?? [];
      const outRow = rows.find(row => row.candidate?.description === outDescription);
      const inRow = rows.find(row => row.candidate?.description === inDescription);
      outCandidateId = outRow?.candidateId;
      inCandidateId = inRow?.candidateId;
      expect(outCandidateId).to.be.a('number');
      expect(inCandidateId).to.be.a('number');
      expect(outRow?.candidate?.flow).to.equal('OUT');
      expect(outRow?.suggestedCategory?.categoryId).to.equal(expenseCategory?.id);
      expect(outRow?.suggestedTags?.map(suggestedTag => suggestedTag.tagId)).to.include(tag?.id);
      expect(inRow?.candidate?.flow).to.equal('IN');
      expect(inRow?.suggestedCategory).to.equal(null);
      expect(inRow?.suggestedTags ?? []).to.have.length(0);
    });
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="workflowClassificationControls"]').should('exist');
    cy.get('[data-cy="workflowContinueClassification"]').should('not.exist');
    cy.get('[data-cy="workflowClassificationReview"]').should('not.exist');
    cy.contains('[data-cy="workflowRows"] tr', outDescription).as('outClassificationRow');
    cy.contains('[data-cy="workflowRows"] tr', inDescription).as('inClassificationRow');

    cy.get('@outClassificationRow').within(() => {
      cy.contains(outDescription).should('be.visible');
      cy.contains(expenseCategory?.name as string).should('be.visible');
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', '');
      cy.contains('button', /Apply suggestions|Aplicar sugerencias/i).click();
    });
    cy.wait('@applyCandidateRulesRequest').its('response.statusCode').should('eq', 200);
    cy.get('@outClassificationRow').within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });

    cy.get('@inClassificationRow').within(() => {
      cy.contains(inDescription).should('be.visible');
      cy.contains(expenseCategory?.name as string).should('not.exist');
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', '');
      cy.get('[data-testid^="classificationCategory-"] option')
        .then(options => Array.from(options).map(option => (option as HTMLOptionElement).textContent))
        .should(optionLabels => {
          expect(optionLabels).not.to.include(expenseCategory?.name);
          expect(optionLabels).to.include(incomeCategory?.name);
        });
      cy.contains('button', /Confirm no suggestions|Confirmar sin sugerencias/i).click();
    });
    cy.wait('@confirmNoSuggestionsRequest').its('response.statusCode').should('eq', 200);

    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewAfterReloadRequest');
    cy.reload();
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewAfterReloadRequest').its('response.statusCode').should('eq', 200);
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });

    cy.intercept('POST', '/api/transaction-ingestions/*/confirm').as('confirmImportRequest');
    cy.get('[data-cy="workflowConfirmImport"]').click();
    cy.wait('@confirmImportRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.status).to.equal('COMPLETED');
      expect(request.body === null || request.body === undefined || request.body === '' || request.body === 'null').to.equal(true);
      expect(JSON.stringify(request.body ?? '')).not.to.contain('records');
      expect(JSON.stringify(request.body ?? '')).not.to.contain('categoryId');
      expect(JSON.stringify(request.body ?? '')).not.to.contain('tagIds');
    });
    cy.then(() => {
      expect(oldClassificationPreviewCalled).to.equal(false);
      expect(financialTransactionRulePreviewCalled).to.equal(false);
      expect(outCandidateId).to.be.a('number');
      expect(inCandidateId).to.be.a('number');
    });

    cy.get('[data-cy="workflowCompleted"]').should('exist');
    cy.reload();
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="workflowCompleted"]').should('exist');
    cy.get('[data-cy="workflowMetadata"]').should('exist');
    cy.get('[data-cy="workflowRows"]').should('contain', outDescription).and('contain', inDescription);
    cy.get('[data-cy="workflowConfirmImport"]').should('not.exist');
    cy.get('[data-testid^="workflowRowEdit-"]').should('not.exist');
    cy.get('[data-testid^="workflowRowDisable-"]').should('not.exist');
    cy.get('[data-testid^="workflowRowEnable-"]').should('not.exist');

    cy.then(() => {
      cy.authenticatedRequest({
        method: 'GET',
        url: `/api/financial-transactions?transactionIngestionId.equals=${transactionIngestionId}`,
      }).then(({ body: transactions }) => {
        const outTransaction = transactions.find(transaction => transaction.description === outDescription);
        const inTransaction = transactions.find(transaction => transaction.description === inDescription);

        expect(outTransaction?.flow).to.equal('OUT');
        expect(outTransaction?.category?.id).to.equal(expenseCategory?.id);
        expect(outTransaction?.tags?.map(transactionTag => transactionTag.id)).to.include(tag?.id);
        expect(inTransaction?.flow).to.equal('IN');
        expect(inTransaction?.category).to.equal(null);
        expect(inTransaction?.tags ?? []).to.have.length(0);
      });

      cy.authenticatedRequest({
        method: 'GET',
        url: `/api/ingestion-records?transactionIngestionId.equals=${transactionIngestionId}`,
      }).then(({ body: records }) => {
        expect(records).to.have.length(2);
        records.forEach(record => {
          const rawData = JSON.parse(record.rawData);
          expect(rawData.normalized).not.to.have.property('category');
          expect(rawData.normalized).not.to.have.property('categoryId');
          expect(rawData.normalized).not.to.have.property('tags');
          expect(rawData.normalized).not.to.have.property('tagIds');
          expect(rawData.review ?? {}).not.to.have.property('category');
          expect(rawData.review ?? {}).not.to.have.property('tags');
        });
      });
    });
  });

  it('shows an error and stays on create when the CSV header is invalid', () => {
    const invalidHeaderCsv = [
      'transactiondate,postingDate,description,signedAmount,currency,externalReference,notes',
      `2026-01-16,,${outDescription},-100.00,MXN,,`,
    ].join('\n');

    uploadCsvFromCreatePage(invalidHeaderCsv, 'invalid-header.csv', 400);

    cy.url().should('match', /\/transaction-ingestion\/new$/);
    cy.contains('CSV header must match the canonical FINTRACK header').should('be.visible');
    cy.get('[data-cy="workflowReviewHeading"]').should('not.exist');
  });

  it('shows rejected rows and blocks category/tag review for PARTIALLY_READY uploads', () => {
    const invalidDescription = `${scenarioToken} invalid zero amount`;
    const validDescription = `${scenarioToken} valid expense`;
    const mixedCsv = [
      header,
      `2026-01-16,,${validDescription},-50.00,MXN,,valid row`,
      `2026-01-17,,${invalidDescription},0,MXN,,invalid row`,
    ].join('\n');

    uploadCsvFromCreatePage(mixedCsv, 'partially-ready.csv');

    cy.url().should('match', /\/transaction-ingestion\/\d+$/);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="workflowRows"]').should('contain', validDescription).and('contain', invalidDescription);
    cy.contains('[data-cy="workflowRows"] tr', invalidDescription).within(() => {
      cy.get('[data-testid^="workflowRowStatus-"]').should('exist');
      cy.contains(/signedAmount|nonzero|zero|cero/i).should('exist');
    });
    cy.get('[data-cy="workflowConfirmBlocked"]').should('exist');
    cy.get('[data-cy="workflowContinueClassification"]').should('not.exist');
    cy.get('[data-cy="workflowClassificationReview"]').should('not.exist');
  });

  it('imports only enabled valid rows when one row is disabled before confirm', () => {
    const enabledDescription = `${scenarioToken} enabled import`;
    const disabledDescription = `${scenarioToken} disabled import`;
    const csv = [
      header,
      `2026-01-16,,${enabledDescription},-30.00,MXN,,enabled row`,
      `2026-01-17,,${disabledDescription},-40.00,MXN,,disabled row`,
    ].join('\n');

    cy.intercept('POST', '**/api/transaction-ingestions/*/records/*/disable').as('disableRecordRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/prepare').as('prepareCandidatesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/apply-rules').as('applyCandidateRulesRequest');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');

    uploadCsvFromCreatePage(csv, 'disable-one-before-confirm.csv');

    let currentIngestionId: number;
    let disabledRecordId: number;
    cy.wait('@workflowRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      currentIngestionId = response?.body.transactionIngestionId;
      expect(currentIngestionId).to.be.a('number');
      const disabledRow = response?.body.rows.find(row => row.description === disabledDescription);
      disabledRecordId = disabledRow?.ingestionRecordId;
      expect(disabledRecordId).to.be.a('number');
    });
    cy.wait('@prepareCandidatesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);
    cy.contains('[data-cy="workflowRows"] tr', disabledDescription).within(() => {
      cy.then(() => {
        expect(disabledRecordId).to.be.a('number');
        cy.contains(disabledDescription).should('be.visible');
        cy.contains('button', /Disable|Deshabilitar/i).click();
      });
    });
    cy.wait('@disableRecordRequest').its('response.statusCode').should('eq', 200);
    cy.contains('[data-cy="workflowRows"] tr', disabledDescription).within(() => {
      cy.contains(/Disabled|Deshabilitada/i).should('exist');
      cy.contains(/Enable|Habilitar/i).should('exist');
    });

    cy.contains('[data-cy="workflowRows"] tr', enabledDescription).within(() => {
      cy.contains('button', /Apply suggestions|Aplicar sugerencias/i).click();
    });
    cy.wait('@applyCandidateRulesRequest').its('response.statusCode').should('eq', 200);

    cy.intercept('POST', '/api/transaction-ingestions/*/confirm').as('confirmImportRequest');
    cy.get('[data-cy="workflowConfirmImport"]').click();
    cy.wait('@confirmImportRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.status).to.equal('COMPLETED');
      expect(request.body === null || request.body === undefined || request.body === '' || request.body === 'null').to.equal(true);
      expect(JSON.stringify(request.body ?? '')).not.to.contain('records');
      expect(JSON.stringify(request.body ?? '')).not.to.contain('categoryId');
      expect(JSON.stringify(request.body ?? '')).not.to.contain('tagIds');
      expect(response?.body.createdNow).to.equal(1);
      expect(response?.body.skipped).to.equal(1);
    });

    cy.then(() => {
      cy.authenticatedRequest({
        method: 'GET',
        url: `/api/financial-transactions?transactionIngestionId.equals=${currentIngestionId}`,
      }).then(({ body: transactions }) => {
        expect(transactions).to.have.length(1);
        expect(transactions[0].description).to.equal(enabledDescription);
      });
    });
  });
});
