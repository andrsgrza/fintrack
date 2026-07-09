import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityCreateCancelButtonSelector,
  entityCreateSaveButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('InternalTransfer e2e test', () => {
  const internalTransferPageUrl = '/internal-transfer';
  const internalTransferPageUrlPattern = new RegExp('/internal-transfer(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let internalTransfer;
  let outgoingTransaction;
  let incomingTransaction;
  let outgoingAccount;
  let incomingAccount;

  const buildFinancialAccountPayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      institutionName: 'E2E Bank',
      accountType: 'DEBIT',
      currency: 'MXN',
      initialBalance: 1000,
      initialBalanceDate: '2026-07-08',
      active: true,
      createdAt: now,
      updatedAt: now,
    };
  };

  const buildFinancialTransactionPayload = (accountId: number, flow: 'OUT' | 'IN', amount: number) => {
    const now = new Date().toISOString();
    return {
      transactionDate: '2026-07-08',
      description: `E2E ${flow} transfer leg`,
      amount,
      flow,
      origin: 'MANUAL',
      createdAt: now,
      updatedAt: now,
      account: { id: accountId },
    };
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    const suffix = `${Date.now()}`;
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: buildFinancialAccountPayload(`transfer-out-${suffix}`),
    }).then(({ body }) => {
      outgoingAccount = body;
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(`transfer-in-${suffix}`),
      }).then(({ body: incomingBody }) => {
        incomingAccount = incomingBody;
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(outgoingAccount.id, 'OUT', 250),
        }).then(({ body: outgoingBody }) => {
          outgoingTransaction = outgoingBody;
          cy.authenticatedRequest({
            method: 'POST',
            url: '/api/financial-transactions',
            body: buildFinancialTransactionPayload(incomingAccount.id, 'IN', 250),
          }).then(({ body: incomingBodyTx }) => {
            incomingTransaction = incomingBodyTx;
          });
        });
      });
    });
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/internal-transfers+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/internal-transfers').as('postEntityRequest');
    cy.intercept('DELETE', '/api/internal-transfers/*').as('deleteEntityRequest');
  });

  afterEach(() => {
    if (internalTransfer) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/internal-transfers/${internalTransfer.id}`,
      }).then(() => {
        internalTransfer = undefined;
      });
    }
  });

  afterEach(() => {
    if (outgoingTransaction) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${outgoingTransaction.id}`,
      }).then(() => {
        outgoingTransaction = undefined;
      });
    }
  });

  afterEach(() => {
    if (incomingTransaction) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${incomingTransaction.id}`,
      }).then(() => {
        incomingTransaction = undefined;
      });
    }
  });

  afterEach(() => {
    if (outgoingAccount) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${outgoingAccount.id}`,
      }).then(() => {
        outgoingAccount = undefined;
      });
    }
  });

  afterEach(() => {
    if (incomingAccount) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${incomingAccount.id}`,
      }).then(() => {
        incomingAccount = undefined;
      });
    }
  });

  it('InternalTransfers menu should load InternalTransfers page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('internal-transfer');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('InternalTransfer').should('exist');
    cy.url().should('match', internalTransferPageUrlPattern);
  });

  describe('InternalTransfer page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.intercept('GET', '/api/financial-transactions/outgoing-internal-transfer-candidates', {
          statusCode: 200,
          body: [outgoingTransaction],
        });
        cy.intercept('GET', '/api/financial-transactions/incoming-internal-transfer-candidates', {
          statusCode: 200,
          body: [incomingTransaction],
        });
        cy.visit(internalTransferPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create InternalTransfer page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/internal-transfer/new$'));
        cy.getEntityCreateUpdateHeading('InternalTransfer');
        cy.get('[data-cy="createdAt"]').should('not.exist');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/internal-transfers',
          body: {
            notes: 'E2E internal transfer',
            outgoingTransaction: { id: outgoingTransaction.id },
            incomingTransaction: { id: incomingTransaction.id },
          },
        }).then(({ body }) => {
          internalTransfer = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/internal-transfers+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [internalTransfer],
            },
          ).as('entitiesRequestInternal');
        });

        cy.visit(internalTransferPageUrl);
        cy.wait('@entitiesRequestInternal');
      });

      it('detail button click should load details InternalTransfer page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('internalTransfer');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });

      it('edit button click should load edit InternalTransfer page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('InternalTransfer');
        cy.get('[data-cy="outgoingTransaction"]').should('have.attr', 'readonly');
        cy.get('[data-cy="incomingTransaction"]').should('have.attr', 'readonly');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });

      it('edit button click should load edit InternalTransfer page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('InternalTransfer');
        cy.get('[data-cy="notes"]').clear().type('updated transfer notes');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);
      });

      it('last delete button click should delete instance of InternalTransfer', () => {
        cy.intercept('GET', '/api/internal-transfers/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('internalTransfer').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', internalTransferPageUrlPattern);

        internalTransfer = undefined;
      });
    });
  });

  describe('new InternalTransfer page', () => {
    beforeEach(() => {
      cy.intercept('GET', '/api/financial-transactions/outgoing-internal-transfer-candidates', {
        statusCode: 200,
        body: [outgoingTransaction],
      });
      cy.intercept('GET', '/api/financial-transactions/incoming-internal-transfer-candidates', {
        statusCode: 200,
        body: [incomingTransaction],
      });
      cy.visit(`${internalTransferPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('InternalTransfer');
    });

    it('should create an instance of InternalTransfer', () => {
      cy.get(`[data-cy="notes"]`).type('E2E linked transfer');
      cy.get(`[data-cy="outgoingTransaction"]`).select(String(outgoingTransaction.id));
      cy.get(`[data-cy="incomingTransaction"]`).select(String(incomingTransaction.id));

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        internalTransfer = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', internalTransferPageUrlPattern);
    });
  });
});
