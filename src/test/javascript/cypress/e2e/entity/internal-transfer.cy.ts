describe('Internal Transfers product workflow', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  type E2EEntity = { id: number; name: string; [key: string]: unknown };
  type TransferRead = E2EEntity & {
    transferId: number;
    version: number;
    source: { transactionId: number };
    destination: { transactionId: number };
  };

  let sourceAccount: E2EEntity;
  let destinationAccount: E2EEntity;
  let otherCurrencyAccount: E2EEntity;
  let transfer: TransferRead | undefined;
  let evidenceTransfers: TransferRead[] = [];

  const captureEvidence = String(Cypress.env('captureInternalTransferEvidence')) === 'true';
  const capture = (name: string) => {
    if (captureEvidence) {
      cy.screenshot(`internal-transfer-acceptance/${name}`, { capture: 'viewport' });
    }
  };

  const unique = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const createAccount = (name: string, currency = 'MXN') =>
    cy
      .authenticatedRequest<E2EEntity>({
        method: 'POST',
        url: '/api/financial-accounts',
        body: {
          name,
          institutionName: 'Internal transfer E2E',
          accountType: 'DEBIT',
          currency,
          initialBalance: 1000,
          initialBalanceDate: '2026-10-17',
          active: true,
        },
      })
      .then(({ body }) => body);

  const unlinkAndRemoveCreatedTransfer = () => {
    if (!transfer) return;
    cy.authenticatedRequest({
      method: 'POST',
      url: `/api/internal-transfers/${transfer.transferId}/unlink`,
      body: { expectedVersion: transfer.version },
      failOnStatusCode: false,
    });
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `/api/financial-transactions/${transfer.source.transactionId}`,
      failOnStatusCode: false,
    });
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `/api/financial-transactions/${transfer.destination.transactionId}`,
      failOnStatusCode: false,
    });
    transfer = undefined;
  };

  const unlinkAndRemoveEvidenceTransfer = (createdTransfer: TransferRead) => {
    cy.authenticatedRequest({
      method: 'POST',
      url: `/api/internal-transfers/${createdTransfer.transferId}/unlink`,
      body: { expectedVersion: createdTransfer.version },
      failOnStatusCode: false,
    });
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `/api/financial-transactions/${createdTransfer.source.transactionId}`,
      failOnStatusCode: false,
    });
    cy.authenticatedRequest({
      method: 'DELETE',
      url: `/api/financial-transactions/${createdTransfer.destination.transactionId}`,
      failOnStatusCode: false,
    });
  };

  beforeEach(() => {
    evidenceTransfers = [];
    cy.login(username, password);
    cy.then(() => createAccount(unique('transfer-source'))).then(account => {
      sourceAccount = account;
    });
    cy.then(() => createAccount(unique('transfer-destination'))).then(account => {
      destinationAccount = account;
    });
    cy.then(() => createAccount(unique('transfer-usd'), 'USD')).then(account => {
      otherCurrencyAccount = account;
    });

    cy.intercept('GET', '/api/internal-transfers/product*').as('productTransfers');
    cy.intercept('GET', '/api/internal-transfers/product/*').as('productTransfer');
    cy.intercept('POST', '/api/internal-transfers/commands/create').as('createTransfer');
    cy.intercept('PUT', '/api/internal-transfers/*/aggregate').as('updateTransfer');
    cy.intercept('POST', '/api/internal-transfers/*/unlink').as('unlinkTransfer');
  });

  afterEach(() => {
    unlinkAndRemoveCreatedTransfer();
    evidenceTransfers.forEach(unlinkAndRemoveEvidenceTransfer);
    [sourceAccount, destinationAccount, otherCurrencyAccount].forEach(account => {
      if (account?.id) {
        cy.authenticatedRequest({ method: 'DELETE', url: `/api/financial-accounts/${account.id}`, failOnStatusCode: false });
      }
    });
  });

  it('shows the filtered empty state and creates a transfer through the atomic product command', () => {
    cy.visit(`/internal-transfers?accountId=${sourceAccount.id}`);
    cy.wait('@productTransfers');
    cy.get('[data-cy="internalTransferEmpty"]').should('contain.text', 'No hay transferencias internas');
    capture('01-empty-state');

    cy.get('[data-cy="internalTransferCreate"]').click();
    cy.get('[data-cy="internalTransferCreateUpdateHeading"]').should('exist');
    capture('03-create-form');
    cy.get('[data-cy="internalTransferSourceAccount"]').select(String(sourceAccount.id));
    cy.get('[data-cy="internalTransferDestinationAccount"]').select(String(destinationAccount.id));
    cy.get('[data-cy="internalTransferAmountInput"]').type('250.50');
    cy.get('#internal-transfer-source-description').type('Salida de prueba');
    cy.get('#internal-transfer-destination-description').type('Entrada de prueba');
    cy.get('#internal-transfer-same-posting-date').uncheck();
    capture('04-posting-dates-expanded');
    cy.get('#internal-transfer-source-posting-date').clear();
    cy.get('#internal-transfer-source-posting-date').type('2026-10-18');
    cy.get('#internal-transfer-destination-posting-date').clear();
    cy.get('#internal-transfer-destination-posting-date').type('2026-10-19');
    cy.get('#internal-transfer-note').type('Nota de transferencia E2E');
    cy.get('[data-cy="saveInternalTransfer"]').click();

    cy.wait('@createTransfer').then(({ request, response }) => {
      expect(request.body).to.include({
        sourceAccountId: sourceAccount.id,
        destinationAccountId: destinationAccount.id,
        amount: 250.5,
        sourcePostingDate: '2026-10-18',
        destinationPostingDate: '2026-10-19',
      });
      expect(response?.statusCode).to.equal(201);
      transfer = response?.body;
    });

    cy.get('[data-cy="internalTransferDetailsHeading"]').should('exist');
    cy.contains('Salida de prueba').should('exist');
    cy.contains('Entrada de prueba').should('exist');
    cy.contains('Nota de transferencia E2E').should('exist');
    cy.contains('250.50 MXN').should('exist');
    capture('06-detail-page');
  });

  it('prevents selecting the same account and exposes a currency mismatch before a command is sent', () => {
    cy.visit('/internal-transfers/new');
    cy.get('[data-cy="internalTransferSourceAccount"]').select(String(sourceAccount.id));
    cy.get(`[data-cy="internalTransferDestinationAccount"] option[value="${sourceAccount.id}"]`).should('not.exist');

    cy.get('[data-cy="internalTransferDestinationAccount"]').select(String(otherCurrencyAccount.id));
    cy.get('[data-cy="internalTransferAmountInput"]').type('10');
    cy.get('#internal-transfer-source-description').type('Salida');
    cy.get('#internal-transfer-destination-description').type('Entrada');
    cy.get('[data-cy="saveInternalTransfer"]').click();
    cy.contains('Las cuentas deben usar la misma moneda.').should('be.visible');
    capture('05-validation-error');
    cy.get('@createTransfer.all').should('have.length', 0);
  });

  it('exposes one canonical product route from entity navigation', () => {
    cy.visit('/');
    cy.get('[data-cy="entity"]').click();
    cy.get('a[href="/internal-transfers"]').should('have.length', 1).click();
    cy.url().should('match', /\/internal-transfers$/);
    cy.wait('@productTransfers');

    cy.visit('/internal-transfer');
    cy.url().should('match', /\/internal-transfers$/);
    cy.get('[data-cy="InternalTransferHeading"]').should('be.visible');
  });

  it('lists human-readable legs, edits as one aggregate, and unlinks without deleting either transaction', () => {
    cy.authenticatedRequest<TransferRead>({
      method: 'POST',
      url: '/api/internal-transfers/commands/create',
      body: {
        sourceAccountId: sourceAccount.id,
        destinationAccountId: destinationAccount.id,
        amount: 75,
        transactionDate: '2026-10-17',
        sourcePostingDate: '2026-10-17',
        destinationPostingDate: '2026-10-17',
        sourceDescription: 'Cuenta origen visible',
        destinationDescription: 'Cuenta destino visible',
        transferNotes: 'Nota existente',
      },
    }).then(({ body }) => {
      transfer = body;
      if (captureEvidence) {
        [
          { amount: 55, sourceDescription: 'Evidencia origen uno', destinationDescription: 'Evidencia destino uno' },
          { amount: 95, sourceDescription: 'Evidencia origen dos', destinationDescription: 'Evidencia destino dos' },
        ].forEach(candidate => {
          cy.authenticatedRequest<TransferRead>({
            method: 'POST',
            url: '/api/internal-transfers/commands/create',
            body: {
              sourceAccountId: sourceAccount.id,
              destinationAccountId: destinationAccount.id,
              amount: candidate.amount,
              transactionDate: '2026-10-17',
              sourcePostingDate: '2026-10-17',
              destinationPostingDate: '2026-10-17',
              sourceDescription: candidate.sourceDescription,
              destinationDescription: candidate.destinationDescription,
              transferNotes: 'Transferencia de evidencia',
            },
          }).then(({ body: evidenceTransfer }) => evidenceTransfers.push(evidenceTransfer));
        });
      }
    });

    cy.visit(`/internal-transfers?accountId=${sourceAccount.id}`);
    cy.wait('@productTransfers');
    cy.get('[data-cy="internalTransferListItem"]').should('contain.text', sourceAccount.name);
    cy.get('[data-cy="internalTransferListItem"]').should('contain.text', destinationAccount.name);
    cy.get('[data-cy="internalTransferListItem"]').should('contain.text', '75.00 MXN');
    capture('02-list-several-transfers');

    cy.get('[data-cy="internalTransferEdit"]').first().click();
    cy.get('[data-cy="internalTransferCreateUpdateHeading"]').should('exist');
    capture('07-edit-page');
    cy.contains('Para cambiar las cuentas, desvincula esta transferencia y crea una nueva.').should('be.visible');
    cy.get('#internal-transfer-amount').clear();
    cy.get('#internal-transfer-amount').type('80');
    cy.get('#internal-transfer-source-description').clear();
    cy.get('#internal-transfer-source-description').type('Origen actualizado');
    cy.get('#internal-transfer-destination-description').clear();
    cy.get('#internal-transfer-destination-description').type('Destino actualizado');
    cy.get('[data-cy="saveInternalTransfer"]').click();
    cy.wait('@updateTransfer').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      transfer = response?.body;
    });
    cy.contains('80.00 MXN').should('exist');

    cy.get('[data-cy="internalTransferDetailUnlink"]').click();
    cy.contains('Las dos transacciones se conservarán y dejarán de estar vinculadas como transferencia interna.').should('be.visible');
    capture('09-unlink-confirmation');
    cy.get('[data-cy="confirmUnlinkInternalTransfer"]').click();
    cy.wait('@unlinkTransfer').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      const preservedTransfer = transfer;
      if (!preservedTransfer) {
        throw new Error('Expected an internal transfer before unlinking it');
      }
      cy.authenticatedRequest({ method: 'GET', url: `/api/financial-transactions/${preservedTransfer.source.transactionId}` })
        .its('status')
        .should('eq', 200);
      cy.authenticatedRequest({ method: 'GET', url: `/api/financial-transactions/${preservedTransfer.destination.transactionId}` })
        .its('status')
        .should('eq', 200);
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${preservedTransfer.source.transactionId}`,
        failOnStatusCode: false,
      });
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${preservedTransfer.destination.transactionId}`,
        failOnStatusCode: false,
      });
      transfer = undefined;
    });
    cy.url().should('include', '/internal-transfers');
    if (captureEvidence) {
      cy.contains('Cuenta origen visible').should('not.exist');
    } else {
      cy.get('[data-cy="internalTransferEmpty"]').should('exist');
    }
  });

  it('keeps local edits visible when a concurrent aggregate update makes its version stale', () => {
    cy.authenticatedRequest<TransferRead>({
      method: 'POST',
      url: '/api/internal-transfers/commands/create',
      body: {
        sourceAccountId: sourceAccount.id,
        destinationAccountId: destinationAccount.id,
        amount: 75,
        transactionDate: '2026-10-17',
        sourcePostingDate: '2026-10-17',
        destinationPostingDate: '2026-10-17',
        sourceDescription: 'Source before conflict',
        destinationDescription: 'Destination before conflict',
        transferNotes: 'Original note',
      },
    }).then(({ body }) => {
      transfer = body;
    });

    cy.then(() => {
      if (!transfer) throw new Error('Expected an internal transfer');
      cy.visit(`/internal-transfers/${transfer.transferId}/edit`);
    });
    cy.wait('@productTransfer');
    cy.get('#internal-transfer-source-description').clear();
    cy.get('#internal-transfer-source-description').type('Local pending source');

    cy.then(() => {
      if (!transfer) throw new Error('Expected an internal transfer');
      return cy
        .authenticatedRequest<TransferRead>({
          method: 'PUT',
          url: `/api/internal-transfers/${transfer.transferId}/aggregate`,
          body: {
            expectedVersion: transfer.version,
            amount: 75,
            transactionDate: '2026-10-17',
            sourcePostingDate: '2026-10-17',
            destinationPostingDate: '2026-10-17',
            sourceDescription: 'Server source',
            destinationDescription: 'Server destination',
            notes: 'Original note',
          },
        })
        .then(({ body }) => {
          transfer = body;
        });
    });

    cy.get('[data-cy="saveInternalTransfer"]').click();
    cy.wait('@updateTransfer').its('response.statusCode').should('eq', 409);
    cy.get('[data-cy="internalTransferFormError"]').should('contain.text', 'Tus cambios siguen aquí');
    cy.get('#internal-transfer-source-description').should('have.value', 'Local pending source');
    capture('08-version-conflict');
  });

  it('keeps the product cards usable at a mobile width', () => {
    cy.authenticatedRequest<TransferRead>({
      method: 'POST',
      url: '/api/internal-transfers/commands/create',
      body: {
        sourceAccountId: sourceAccount.id,
        destinationAccountId: destinationAccount.id,
        amount: 20,
        transactionDate: '2026-10-17',
        sourceDescription: 'Origen móvil',
        destinationDescription: 'Destino móvil',
      },
    }).then(({ body }) => {
      transfer = body;
    });

    cy.viewport('iphone-x');
    cy.visit(`/internal-transfers?accountId=${sourceAccount.id}`);
    cy.wait('@productTransfers');
    cy.get('[data-cy="internalTransferListItem"]').should('be.visible');
    cy.get('[data-cy="internalTransferSourceLeg"]').should('be.visible');
    cy.get('[data-cy="internalTransferDestinationLeg"]').should('be.visible');
    cy.document().its('documentElement.scrollWidth').should('lte', Cypress.config('viewportWidth'));
    capture('10-mobile-list-card');
  });

  it('keeps creation, detail, and edit reachable at a mobile width', () => {
    cy.viewport('iphone-x');
    cy.visit('/internal-transfers/new');
    cy.get('[data-cy="internalTransferSourceAccount"]').select(String(sourceAccount.id));
    cy.get('[data-cy="internalTransferDestinationAccount"]').select(String(destinationAccount.id));
    cy.get('[data-cy="internalTransferAmountInput"]').type('32.50');
    cy.get('#internal-transfer-source-description').type('Salida móvil');
    cy.get('#internal-transfer-destination-description').type('Entrada móvil');
    capture('11-mobile-create-edit');
    cy.get('[data-cy="saveInternalTransfer"]').click();
    cy.wait('@createTransfer').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      transfer = response?.body;
    });
    cy.wait('@productTransfer');
    cy.get('[data-cy="internalTransferDetailsHeading"]').should('be.visible');
    cy.contains('Salida móvil').should('be.visible');
    cy.contains('Entrada móvil').should('be.visible');
    capture('12-mobile-detail');
    cy.get('[data-cy="internalTransferDetailEdit"]').click();
    cy.wait('@productTransfer');
    cy.get('[data-cy="internalTransferCreateUpdateHeading"]').should('be.visible');
    cy.get('#internal-transfer-amount').should('be.visible');
    cy.document().its('documentElement.scrollWidth').should('lte', Cypress.config('viewportWidth'));
  });
});
