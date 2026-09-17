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
  let manualExpenseCategory: E2EEntity | undefined;
  let incomeCategory: E2EEntity | undefined;
  let tag: E2EEntity | undefined;
  let manualTag: E2EEntity | undefined;
  let rule: E2EEntity | undefined;
  let contextualDescriptionRules: E2EEntity[] = [];
  let contextualTransactionRules: E2EEntity[] = [];
  let transactionIngestionId: number | undefined;
  let scenarioToken: string;
  let outDescription: string;
  let secondOutDescription: string;
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
    secondOutDescription = `${scenarioToken} second trip`;
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
        name: uniqueName('Manual transport'),
        description: 'CSV ingestion E2E manual expense category',
        categoryType: 'EXPENSE',
        color: '#663399',
        active: true,
      },
    }).then(({ body }) => {
      manualExpenseCategory = body;
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

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/tags',
      body: {
        name: uniqueName('Manual review'),
        description: 'CSV ingestion E2E manual tag',
        color: '#336666',
        active: true,
      },
    }).then(({ body }) => {
      manualTag = body;
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

    contextualDescriptionRules.forEach(contextualRule => {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/description-normalization-rules/${contextualRule.id}`,
        failOnStatusCode: false,
      });
    });
    contextualDescriptionRules = [];

    contextualTransactionRules.forEach(contextualRule => {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${contextualRule.id}`,
        failOnStatusCode: false,
      });
    });
    contextualTransactionRules = [];

    if (tag?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/tags/${tag.id}`,
        failOnStatusCode: false,
      });
      tag = undefined;
    }

    if (manualTag?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/tags/${manualTag.id}`,
        failOnStatusCode: false,
      });
      manualTag = undefined;
    }

    [expenseCategory, manualExpenseCategory, incomeCategory].forEach(category => {
      if (category?.id) {
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/categories/${category.id}`,
          failOnStatusCode: false,
        });
      }
    });
    expenseCategory = undefined;
    manualExpenseCategory = undefined;
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

  it('applies CSV category and tag suggestions independently while preserving a manual selection and imports reviewed candidates', () => {
    cy.then(() => {
      expect(account?.name).to.be.a('string');
      expect(expenseCategory?.id).to.be.a('number');
      expect(tag?.id).to.be.a('number');
      expect(rule?.id).to.be.a('number');
    });

    const csv = [
      header,
      `2026-01-16,,${outDescription},-100.00,MXN,${scenarioToken}-trip,e2e out row`,
      `2026-01-16,,${secondOutDescription},-50.00,MXN,${scenarioToken}-second-trip,e2e second out row`,
      `2026-01-17,,${inDescription},100.00,MXN,${scenarioToken}-refund,e2e in row`,
    ].join('\n');

    let oldClassificationPreviewCalled = false;
    let financialTransactionRulePreviewCalled = false;
    let candidateApplyCalled = false;
    let confirmNoSuggestionsCalled = false;
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
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/apply-rules', req => {
      candidateApplyCalled = true;
      req.continue();
    }).as('applyCandidateRulesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/*/confirm-no-suggestions', req => {
      confirmNoSuggestionsCalled = true;
      req.continue();
    }).as('confirmNoSuggestionsRequest');
    cy.intercept('PATCH', '/api/transaction-ingestions/*/candidates/*/classification').as('manualClassificationRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/descriptions/reevaluate').as('reevaluateDescriptionsRequest');
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
    let secondOutCandidateId: number;
    let inCandidateId: number;
    let outRecordId: number;
    cy.wait('@candidateRulePreviewRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const rows = response?.body.rows ?? [];
      const outRow = rows.find(row => row.candidate?.description === outDescription);
      const secondOutRow = rows.find(row => row.candidate?.description === secondOutDescription);
      const inRow = rows.find(row => row.candidate?.description === inDescription);
      outCandidateId = outRow?.candidateId;
      secondOutCandidateId = secondOutRow?.candidateId;
      inCandidateId = inRow?.candidateId;
      outRecordId = outRow?.ingestionRecordId;
      expect(outCandidateId).to.be.a('number');
      expect(secondOutCandidateId).to.be.a('number');
      expect(inCandidateId).to.be.a('number');
      expect(outRecordId).to.be.a('number');
      expect(outRow?.candidate?.flow).to.equal('OUT');
      expect(outRow?.suggestedCategory?.categoryId).to.equal(expenseCategory?.id);
      expect(outRow?.suggestedTags?.map(suggestedTag => suggestedTag.tagId)).to.include(tag?.id);
      expect(secondOutRow?.candidate?.flow).to.equal('OUT');
      expect(secondOutRow?.suggestedCategory?.categoryId).to.equal(expenseCategory?.id);
      expect(secondOutRow?.suggestedTags?.map(suggestedTag => suggestedTag.tagId)).to.include(tag?.id);
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
    cy.contains('[data-cy="workflowRows"] tr', secondOutDescription).as('secondOutClassificationRow');
    cy.contains('[data-cy="workflowRows"] tr', inDescription).as('inClassificationRow');

    cy.get('[data-cy="workflowReevaluateDescriptions"]').should('be.visible').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: true });
    });

    cy.get('[data-cy="workflowReevaluateCategories"]').should('be.visible').click();
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'CATEGORY' });
    });

    cy.get('[data-cy="workflowReevaluateTags"]').should('be.visible').click();
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'TAGS' });
    });

    cy.get('[data-cy="workflowReevaluateAll"]').should('be.visible').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: true });
    });
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body === null || request.body === undefined || request.body === '' || request.body === 'null').to.equal(true);
    });

    cy.get('@outClassificationRow').within(() => {
      cy.contains('button', /Reevaluate this row|Reevaluar esta fila/i).click();
    });
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ recordIds: [outRecordId], apply: false, protectManualChanges: true });
    });
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ candidateIds: [outCandidateId], scope: 'ALL' });
    });
    cy.then(() => {
      expect(candidateApplyCalled).to.equal(false);
      expect(confirmNoSuggestionsCalled).to.equal(false);
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
      cy.get('[data-testid^="classificationCategory-"]').select(incomeCategory?.name as string);
    });
    cy.wait('@manualClassificationRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
    });

    cy.get('[data-cy="workflowApplyAllSuggestedCategories"]').should('be.visible').and('be.enabled').click();
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'CATEGORY' });
    });
    cy.get('@outClassificationRow').within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('have.length', 0);
      cy.get('[data-testid^="classificationSuggestedTags-"]').should('contain', tag?.name as string);
      cy.contains(expenseCategory?.name as string).should('be.visible');
    });
    cy.get('@secondOutClassificationRow').within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('have.length', 0);
      cy.get('[data-testid^="classificationSuggestedTags-"]').should('contain', tag?.name as string);
    });
    cy.get('@inClassificationRow').within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(incomeCategory?.id));
    });

    cy.get('[data-cy="workflowApplyAllSuggestedTags"]').should('be.visible').and('be.enabled').click();
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'TAGS' });
    });
    cy.get('@outClassificationRow').within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });
    cy.get('@secondOutClassificationRow').within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });
    cy.then(() => {
      expect(candidateApplyCalled).to.equal(true);
      expect(confirmNoSuggestionsCalled).to.equal(false);
    });

    cy.get('[data-cy="workflowReevaluateCategories"]').click();
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'CATEGORY' });
    });
    cy.get('[data-cy="workflowApplyAllSuggestedCategories"]').should('be.disabled');
    cy.get('@outClassificationRow').within(() => {
      cy.get('[data-testid^="classificationApplySuggestedCategory-"]').should('not.exist');
    });

    cy.get('[data-cy="workflowReevaluateTags"]').click();
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'TAGS' });
    });
    cy.get('[data-cy="workflowApplyAllSuggestedTags"]').should('be.disabled');
    cy.get('@outClassificationRow').within(() => {
      cy.get('[data-testid^="classificationApplySuggestedTags-"]').should('not.exist');
    });

    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewAfterReloadRequest');
    cy.reload();
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewAfterReloadRequest').its('response.statusCode').should('eq', 200);
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });
    cy.contains('[data-cy="workflowRows"] tr', secondOutDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });
    cy.contains('[data-cy="workflowRows"] tr', inDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(incomeCategory?.id));
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
      expect(secondOutCandidateId).to.be.a('number');
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
        const secondOutTransaction = transactions.find(transaction => transaction.description === secondOutDescription);
        const inTransaction = transactions.find(transaction => transaction.description === inDescription);

        expect(outTransaction?.flow).to.equal('OUT');
        expect(outTransaction?.category?.id).to.equal(expenseCategory?.id);
        expect(outTransaction?.tags?.map(transactionTag => transactionTag.id)).to.include(tag?.id);
        expect(secondOutTransaction?.flow).to.equal('OUT');
        expect(secondOutTransaction?.category?.id).to.equal(expenseCategory?.id);
        expect(secondOutTransaction?.tags?.map(transactionTag => transactionTag.id)).to.include(tag?.id);
        expect(inTransaction?.flow).to.equal('IN');
        expect(inTransaction?.category?.id).to.equal(incomeCategory?.id);
        expect(inTransaction?.tags ?? []).to.have.length(0);
      });

      cy.authenticatedRequest({
        method: 'GET',
        url: `/api/ingestion-records?transactionIngestionId.equals=${transactionIngestionId}`,
      }).then(({ body: records }) => {
        expect(records).to.have.length(3);
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

  it('uses local auto-apply configuration without overriding protected manual category or tags', () => {
    const csv = [header, `2026-01-16,,${outDescription},-100.00,MXN,${scenarioToken}-auto,auto configuration row`].join('\n');

    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/prepare').as('prepareCandidatesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/apply-rules').as('applyCandidateRulesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/descriptions/reevaluate').as('reevaluateDescriptionsRequest');
    cy.intercept('PATCH', '/api/transaction-ingestions/*/candidates/*/classification').as('manualClassificationRequest');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/confirm').as('confirmImportRequest');

    uploadCsvFromCreatePage(csv, 'auto-apply-configuration.csv');

    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@prepareCandidatesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="workflowAutoApplyDescriptions"]').should('not.be.checked');
    cy.get('[data-cy="workflowAutoApplyCategories"]').should('not.be.checked');
    cy.get('[data-cy="workflowAutoApplyTags"]').should('not.be.checked');
    cy.get('[data-cy="workflowProtectManualChanges"]').should('be.checked');

    cy.get('[data-cy="workflowAutoApplyCategories"]').check();
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'CATEGORY', automatic: true, protectManualChanges: true });
    });
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
    });

    cy.get('[data-cy="workflowAutoApplyTags"]').check();
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ scope: 'TAGS', automatic: true, protectManualChanges: true });
    });
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
      cy.get('[data-testid^="classificationCategory-"]').select(manualExpenseCategory?.name as string);
    });
    cy.wait('@manualClassificationRequest').its('response.statusCode').should('eq', 200);
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationTags-"]').select([manualTag?.name as string]);
    });
    cy.wait('@manualClassificationRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="workflowReevaluateAll"]').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: true });
    });
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ automatic: true, protectManualChanges: true });
    });
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(manualExpenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', manualTag?.name as string);
    });

    cy.get('[data-cy="workflowProtectManualChanges"]').uncheck();
    cy.get('[data-cy="workflowReevaluateAll"]').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: false });
    });
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ automatic: true, protectManualChanges: false });
    });
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', String(expenseCategory?.id));
      cy.get('[data-testid^="classificationTags-"] option:selected').should('contain', tag?.name as string);
    });

    cy.get('[data-cy="workflowConfirmImport"]').click();
    cy.wait('@confirmImportRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.status).to.equal('COMPLETED');
      expect(request.body === null || request.body === undefined || request.body === '' || request.body === 'null').to.equal(true);
    });
    cy.get('[data-cy="workflowCompleted"]').should('exist');
  });

  it('applies previewed description suggestions explicitly without applying classification or confirming import', () => {
    const firstDescription = `${scenarioToken} explicit description one`;
    const secondDescription = `${scenarioToken} explicit description two`;
    const firstExternalReference = `${scenarioToken}-description-one`;
    const secondExternalReference = `${scenarioToken}-description-two`;
    const normalizedDescription = `Normalized ${scenarioToken}`;
    const csv = [
      header,
      `2026-01-16,,${firstDescription},-100.00,MXN,${firstExternalReference},first description row`,
      `2026-01-17,,${secondDescription},-50.00,MXN,${secondExternalReference},second description row`,
    ].join('\n');
    let classificationApplyCalled = false;
    let confirmCalled = false;
    const workflowRowFor = (externalReference: string) => cy.contains('[data-testid^="workflowRow-"]', externalReference);

    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/prepare').as('prepareCandidatesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/apply-rules', req => {
      classificationApplyCalled = true;
      req.continue();
    }).as('applyCandidateRulesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/descriptions/reevaluate').as('reevaluateDescriptionsRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/confirm', req => {
      confirmCalled = true;
      req.continue();
    }).as('confirmImportRequest');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');

    uploadCsvFromCreatePage(csv, 'explicit-description-apply.csv');
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@prepareCandidatesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/description-normalization-rules/configured',
      body: {
        name: uniqueName('explicit-description-normalization'),
        active: true,
        conditionOperator: 'ALL',
        resultingDescription: normalizedDescription,
        conditions: [{ operator: 'CONTAINS', value: scenarioToken, caseSensitive: false, position: 0 }],
      },
    }).then(({ body }) => {
      contextualDescriptionRules.push(body);
    });

    cy.get('[data-cy="workflowAutoApplyDescriptions"]').should('not.be.checked');
    cy.get('[data-cy="workflowReevaluateDescriptions"]').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: true });
    });
    workflowRowFor(firstExternalReference).within(() => {
      cy.get('[data-testid^="descriptionReview-current-"]').should('contain', firstDescription).and('not.contain', normalizedDescription);
      cy.get('[data-testid^="descriptionReview-badge-"]').should('not.exist');
      cy.get('[data-testid^="descriptionReview-reevaluationSuggestion-"]').should('contain', normalizedDescription);
      cy.get('[data-testid^="workflowApplyDescriptionSuggestion-"]').should('be.visible');
      cy.get('[data-testid^="classificationCategory-"]').should('have.value', '');
      cy.get('[data-testid^="classificationTags-"] option:selected').should('have.length', 0);
    });
    workflowRowFor(firstExternalReference).within(() => {
      cy.get('[data-testid^="workflowApplyDescriptionSuggestion-"]').click();
    });
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ apply: true, protectManualChanges: false });
      expect(request.body.recordIds).to.have.length(1);
      expect(request.body.recordIds[0]).to.be.a('number');
    });
    workflowRowFor(firstExternalReference)
      .should('contain', normalizedDescription)
      .within(() => {
        cy.get('[data-testid^="descriptionReview-badge-"]')
          .invoke('text')
          .should('match', /Auto-normalized|Normalizada automáticamente/);
        cy.get('[data-testid^="classificationCategory-"]').should('have.value', '');
        cy.get('[data-testid^="classificationTags-"] option:selected').should('have.length', 0);
      });

    cy.get('[data-cy="workflowApplyAllDescriptionSuggestions"]').should('be.enabled').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ apply: true, protectManualChanges: true });
      expect(request.body.recordIds).to.have.length(1);
      expect(request.body.recordIds[0]).to.be.a('number');
    });
    workflowRowFor(secondExternalReference)
      .should('contain', normalizedDescription)
      .within(() => {
        cy.get('[data-testid^="descriptionReview-badge-"]')
          .invoke('text')
          .should('match', /Auto-normalized|Normalizada automáticamente/);
      });
    cy.then(() => {
      expect(classificationApplyCalled).to.equal(false);
      expect(confirmCalled).to.equal(false);
    });
  });

  it('does not offer a no-op description suggestion after reevaluation', () => {
    const description = `${scenarioToken} already normalized`;
    const csv = [header, `2026-01-16,,${description},-100.00,MXN,${scenarioToken}-no-op,no-op description row`].join('\n');

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/description-normalization-rules/configured',
      body: {
        name: uniqueName('no-op-description-normalization'),
        active: true,
        conditionOperator: 'ALL',
        resultingDescription: description,
        conditions: [{ operator: 'CONTAINS', value: scenarioToken, caseSensitive: false, position: 0 }],
      },
    }).then(({ body }) => {
      contextualDescriptionRules.push(body);
    });

    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/prepare').as('prepareCandidatesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview').as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/descriptions/reevaluate').as('reevaluateDescriptionsRequest');

    uploadCsvFromCreatePage(csv, 'no-op-description-suggestion.csv');
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@prepareCandidatesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewRequest').its('response.statusCode').should('eq', 200);

    cy.contains('[data-cy="workflowRows"] tr', description).within(() => {
      cy.get('[data-testid^="descriptionReview-current-"]').should('contain', description);
    });
    cy.get('[data-cy="workflowReevaluateDescriptions"]').click();
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: true });
    });
    cy.contains('[data-cy="workflowRows"] tr', description).within(() => {
      cy.get('[data-testid^="descriptionReview-reevaluationSuggestion-"]').should('not.exist');
      cy.get('[data-testid^="workflowApplyDescriptionSuggestion-"]').should('not.exist');
    });
    cy.get('[data-cy="workflowApplyAllDescriptionSuggestions"]').should('be.visible').and('be.disabled');
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

  it('creates global and row-contextual rules without mutating review state until an explicit reevaluation', () => {
    const csv = [header, `2026-01-16,,${outDescription},-100.00,MXN,${scenarioToken}-context,contextual rule row`].join('\n');
    let descriptionReevaluationCalls = 0;
    let candidatePreviewCalls = 0;

    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/prepare').as('prepareCandidatesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/rule-preview', req => {
      candidatePreviewCalls += 1;
      req.continue();
    }).as('candidateRulePreviewRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/candidates/apply-rules').as('applyCandidateRulesRequest');
    cy.intercept('POST', '/api/transaction-ingestions/*/descriptions/reevaluate', req => {
      descriptionReevaluationCalls += 1;
      req.continue();
    }).as('reevaluateDescriptionsRequest');
    cy.intercept('POST', '/api/description-normalization-rules/configured').as('createConfiguredNormalizationRule');
    cy.intercept('POST', '/api/transaction-rules/configured').as('createConfiguredTransactionRule');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');

    uploadCsvFromCreatePage(csv, 'contextual-rule-creation.csv');

    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@prepareCandidatesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@workflowRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@candidateRulePreviewRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);

    cy.get('[data-cy="workflowNewNormalizationRule"]').click();
    cy.get('[data-cy="transactionIngestionRuleCreationModal"]').should('be.visible');
    cy.get('#contextual-description-rule-name').type(uniqueName('global-normalization'));
    cy.get('#contextual-description-rule-result').type('Normalized global description');
    cy.contains('button', /Add condition|Agregar condición/i).click();
    cy.get('[data-testid="contextualDescriptionRuleConditionValue-0"]').type(scenarioToken);
    cy.get('[data-cy="contextualRuleSave"]').click();
    cy.wait('@createConfiguredNormalizationRule').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body.conditions).to.deep.include({ operator: 'CONTAINS', value: scenarioToken, caseSensitive: false, position: 0 });
      contextualDescriptionRules.push(response?.body);
    });
    cy.then(() => expect(descriptionReevaluationCalls).to.equal(0));

    cy.get('[data-cy="workflowNewTransactionRule"]').click();
    cy.get('#contextual-transaction-rule-name').type(uniqueName('global-transaction'));
    cy.get('[data-cy="resultingTags"]').select(tag?.name as string);
    cy.get('[data-cy="addConditionButton"]').click();
    cy.get('[data-cy="embeddedConditionForm"]').within(() => {
      cy.get('[data-cy="operator"]').select('CONTAINS');
      cy.get('[data-cy="value"]').type(scenarioToken);
      cy.get('[data-cy="conditionSaveButton"]').click();
    });
    cy.get('[data-cy="contextualRuleSave"]').click();
    cy.wait('@createConfiguredTransactionRule').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(request.body.conditions).to.satisfy(conditions =>
        conditions.some(
          condition => condition.field === 'DESCRIPTION' && condition.operator === 'CONTAINS' && condition.value === scenarioToken,
        ),
      );
      contextualTransactionRules.push(response?.body);
    });

    cy.get('[data-cy="workflowNewNormalizationRule"]').click();
    cy.get('#contextual-description-rule-name').type(uniqueName('global-normalization-reevaluate-all'));
    cy.get('#contextual-description-rule-result').type('Normalized after global reevaluation');
    cy.get('[data-cy="contextualRuleSaveAndReevaluateAll"]').click();
    cy.wait('@createConfiguredNormalizationRule').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      contextualDescriptionRules.push(response?.body);
    });
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.deep.equal({ apply: false, protectManualChanges: true });
    });

    cy.then(() => {
      candidatePreviewCalls = 0;
      descriptionReevaluationCalls = 0;
    });
    cy.get('[data-cy^="workflowRowCreateRuleToggle-"]').first().scrollIntoView().click();
    cy.get('[data-testid^="workflowRowCreateNormalization-"]').click();
    cy.get('#contextual-description-rule-result').should('have.value', outDescription);
    cy.get('[data-testid="contextualDescriptionRuleConditionValue-0"]').should('have.value', outDescription);
    cy.get('#contextual-description-rule-name').clear().type(uniqueName('row-normalization'));
    cy.get('[data-cy="contextualRuleSaveAndReevaluateRow"]').click();
    cy.wait('@createConfiguredNormalizationRule').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      contextualDescriptionRules.push(response?.body);
    });
    cy.wait('@reevaluateDescriptionsRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ apply: false, protectManualChanges: true });
      expect(request.body.recordIds).to.have.length(1);
      expect(request.body.recordIds[0]).to.be.a('number');
    });

    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationApplySuggestedTags-"]').click();
    });
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.scope).to.equal('TAGS');
    });
    cy.contains('[data-cy="workflowRows"] tr', outDescription).within(() => {
      cy.get('[data-testid^="classificationApplySuggestedCategory-"]').click();
    });
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.scope).to.equal('CATEGORY');
    });
    cy.then(() => expect(candidatePreviewCalls).to.equal(0));

    cy.get('[data-cy^="workflowRowCreateRuleToggle-"]').first().scrollIntoView().click();
    cy.get('[data-testid^="workflowRowCreateTransaction-"]').click();
    cy.get('#contextual-transaction-rule-category').should('have.value', String(expenseCategory?.id));
    cy.get('#contextual-transaction-rule-tags option:selected').should('contain', tag?.name as string);
    cy.get('[data-cy="transactionRuleConfiguredConditionsEditor"] [data-condition-field="DESCRIPTION"]').should(
      'have.attr',
      'data-condition-value',
      outDescription,
    );
    cy.get('[data-cy="transactionRuleConfiguredConditionsEditor"] [data-condition-field="FLOW"]').should(
      'have.attr',
      'data-condition-value',
      'OUT',
    );
    cy.get('[data-cy="transactionRuleConfiguredConditionsEditor"] [data-condition-field="ACCOUNT"]').should('not.exist');
    const rowTransactionRuleName = uniqueName('row-transaction');
    cy.get('#contextual-transaction-rule-name').clear().type(rowTransactionRuleName);
    cy.get('[data-cy="contextualRuleSaveAndReevaluateRow"]').click();
    cy.wait('@createConfiguredTransactionRule').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      contextualTransactionRules.push(response?.body);
    });
    cy.wait('@candidateRulePreviewRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body).to.include({ scope: 'ALL' });
      expect(request.body.candidateIds).to.have.length(1);
      const previewedRow = response?.body.rows.find(row => row.candidate?.description === outDescription);
      expect(previewedRow?.matchedRules.map(rule => rule.ruleName)).to.include(rowTransactionRuleName);
    });
    cy.then(() => {
      expect(candidatePreviewCalls).to.equal(1);
      expect(descriptionReevaluationCalls).to.equal(1);
    });
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
      cy.get('[data-testid^="classificationApplySuggestedCategory-"]').click();
    });
    cy.wait('@applyCandidateRulesRequest').then(({ request, response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(request.body.scope).to.equal('CATEGORY');
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
