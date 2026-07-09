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

describe('ApiAccessToken e2e test', () => {
  const apiAccessTokenPageUrl = '/api-access-token';
  const apiAccessTokenPageUrlPattern = new RegExp('/api-access-token(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let apiAccessToken;

  const buildCreatePayload = (suffix: string) => {
    const now = new Date().toISOString();
    return {
      name: `token-${suffix}`,
      tokenPrefix: 'ftk_',
      tokenHash: `hash-${suffix}`,
      status: 'ACTIVE',
      createdAt: now,
      updatedAt: now,
    };
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/api-access-tokens+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/api-access-tokens').as('postEntityRequest');
    cy.intercept('DELETE', '/api/api-access-tokens/*').as('deleteEntityRequest');
  });

  afterEach(() => {
    if (apiAccessToken) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/api-access-tokens/${apiAccessToken.id}`,
      }).then(() => {
        apiAccessToken = undefined;
      });
    }
  });

  it('ApiAccessTokens menu should load ApiAccessTokens page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('api-access-token');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('ApiAccessToken').should('exist');
    cy.url().should('match', apiAccessTokenPageUrlPattern);
  });

  describe('ApiAccessToken page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(apiAccessTokenPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create ApiAccessToken page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/api-access-token/new$'));
        cy.getEntityCreateUpdateHeading('ApiAccessToken');
        cy.get('[data-cy="user"]').should('not.exist');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        const suffix = `${Date.now()}`;
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/api-access-tokens',
          body: buildCreatePayload(suffix),
        }).then(({ body }) => {
          apiAccessToken = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/api-access-tokens+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [apiAccessToken],
            },
          ).as('entitiesRequestInternal');
        });

        cy.visit(apiAccessTokenPageUrl);
        cy.wait('@entitiesRequestInternal');
      });

      it('detail button click should load details ApiAccessToken page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('apiAccessToken');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPageUrlPattern);
      });

      it('edit button click should load edit ApiAccessToken page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('ApiAccessToken');
        cy.get('[data-cy="tokenHash"]').should('not.exist');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPageUrlPattern);
      });

      it('edit button click should load edit ApiAccessToken page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('ApiAccessToken');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPageUrlPattern);
      });

      it('last delete button click should delete instance of ApiAccessToken', () => {
        cy.intercept('GET', '/api/api-access-tokens/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('apiAccessToken').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPageUrlPattern);

        apiAccessToken = undefined;
      });
    });
  });

  describe('new ApiAccessToken page', () => {
    beforeEach(() => {
      cy.visit(`${apiAccessTokenPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('ApiAccessToken');
    });

    it('should create an instance of ApiAccessToken', () => {
      const suffix = `${Date.now()}`;
      cy.get('[data-cy="name"]').type(`token-${suffix}`);
      cy.get('[data-cy="tokenPrefix"]').type('ftk_');
      cy.get('[data-cy="tokenHash"]').type(`hash-${suffix}`);
      cy.get('[data-cy="status"]').select('ACTIVE');
      cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
      cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
      cy.get('[data-cy="user"]').should('not.exist');

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.user.login).to.equal(username);
        expect(response?.body.tokenHash).to.equal(undefined);
        apiAccessToken = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', apiAccessTokenPageUrlPattern);
    });
  });
});
