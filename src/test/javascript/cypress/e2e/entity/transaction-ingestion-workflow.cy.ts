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

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

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
      'transactionDate,postingDate,description,signedAmount,currency,externalReference,notes',
      `2026-01-16,,${outDescription},-100.00,MXN,${scenarioToken}-trip,e2e out row`,
      `2026-01-17,,${inDescription},100.00,MXN,${scenarioToken}-refund,e2e in row`,
    ].join('\n');

    cy.intercept('GET', '/api/financial-accounts+(?*|)').as('accountsRequest');
    cy.intercept('POST', '/api/transaction-ingestions/file').as('createWorkflowRequest');
    cy.intercept('GET', '/api/transaction-ingestions/*/workflow').as('workflowRequest');
    cy.visit('/transaction-ingestion/new');
    cy.wait('@accountsRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="account"]').select(account?.name as string);
    cy.get('[data-cy="ingestionType"]').select('FILE');
    cy.get('[data-cy="csvFile"]').selectFile({
      contents: Cypress.Buffer.from(csv),
      fileName: 'uber-flow-classification.csv',
      mimeType: 'text/csv',
      lastModified: Date.now(),
    });
    cy.get('[data-cy="entityCreateSaveButton"]').click();
    cy.wait('@createWorkflowRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      transactionIngestionId = response?.body.transactionIngestionId;
      expect(transactionIngestionId).to.be.a('number');
    });

    cy.url().should('match', /\/transaction-ingestion\/\d+$/);
    cy.get('[data-cy="workflowReviewHeading"]').should('exist');
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="workflowRows"]').should('contain', outDescription).and('contain', inDescription);

    cy.intercept('POST', '/api/transaction-ingestions/*/classification-preview').as('classificationPreviewRequest');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');
    cy.get('[data-cy="workflowContinueClassification"]').click();
    cy.wait('@classificationPreviewRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const rows = response?.body.rows ?? [];
      const outRow = rows.find(row => row.description === outDescription);
      const inRow = rows.find(row => row.description === inDescription);
      expect(outRow?.flow).to.equal('OUT');
      expect(outRow?.suggestedCategory?.id).to.equal(expenseCategory?.id);
      expect(outRow?.suggestedTags?.map(suggestedTag => suggestedTag.id)).to.include(tag?.id);
      expect(inRow?.flow).to.equal('IN');
      expect(inRow?.suggestedCategory).to.equal(null);
      expect(inRow?.suggestedTags ?? []).to.have.length(0);
    });
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="workflowClassificationReview"]').should('exist');
    cy.contains('[data-testid^="classificationRow-"]', outDescription).as('outClassificationRow');
    cy.contains('[data-testid^="classificationRow-"]', inDescription).as('inClassificationRow');

    cy.get('@outClassificationRow').within(() => {
      cy.contains(outDescription).should('be.visible');
      cy.contains(expenseCategory?.name as string).should('be.visible');
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
    });

    cy.intercept('POST', '/api/transaction-ingestions/*/confirm').as('confirmImportRequest');
    cy.get('[data-cy="workflowConfirmImport"]').click();
    cy.wait('@confirmImportRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.status).to.equal('COMPLETED');
      const outSelection = request.body.records.find(record => record.categoryId === expenseCategory?.id);
      const inSelection = request.body.records.find(record => record.categoryId === null);
      expect(outSelection?.tagIds).to.include(tag?.id);
      expect(inSelection?.tagIds).to.deep.equal([]);
    });

    cy.get('[data-cy="workflowCompleted"]').should('exist');

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
});
