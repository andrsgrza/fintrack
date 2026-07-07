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
  // const apiAccessTokenSample = {"name":"as once","tokenPrefix":"rosy","tokenHash":"wetly regarding","status":"EXPIRED","createdAt":"2026-07-07T02:47:56.934Z","updatedAt":"2026-07-07T16:09:46.265Z"};

  let apiAccessToken;
  // let user;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/users',
      body: {"login":"A9Tfw","firstName":"Inés","lastName":"Guillén Márquez","email":"Jacobo21@hotmail.com","imageUrl":"vamoose molasses","langKey":"amidst ove"},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/api-access-tokens+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/api-access-tokens').as('postEntityRequest');
    cy.intercept('DELETE', '/api/api-access-tokens/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/users', {
      statusCode: 200,
      body: [user],
    });

    cy.intercept('GET', '/api/api-ingestions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/api-access-token-permissions', {
      statusCode: 200,
      body: [],
    });

  });
   */

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

  /* Disabled due to incompatibility
  afterEach(() => {
    if (user) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/users/${user.id}`,
      }).then(() => {
        user = undefined;
      });
    }
  });
   */

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
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/api-access-tokens',
          body: {
            ...apiAccessTokenSample,
            user: user,
          },
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
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(apiAccessTokenPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(apiAccessTokenPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
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

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of ApiAccessToken', () => {
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

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of ApiAccessToken', () => {
      cy.get(`[data-cy="name"]`).type('provided');
      cy.get(`[data-cy="name"]`).should('have.value', 'provided');

      cy.get(`[data-cy="tokenPrefix"]`).type('whoa');
      cy.get(`[data-cy="tokenPrefix"]`).should('have.value', 'whoa');

      cy.get(`[data-cy="tokenHash"]`).type('ick');
      cy.get(`[data-cy="tokenHash"]`).should('have.value', 'ick');

      cy.get(`[data-cy="status"]`).select('REVOKED');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T15:27');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T15:27');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T12:29');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T12:29');

      cy.get(`[data-cy="lastUsedAt"]`).type('2026-07-06T20:44');
      cy.get(`[data-cy="lastUsedAt"]`).blur();
      cy.get(`[data-cy="lastUsedAt"]`).should('have.value', '2026-07-06T20:44');

      cy.get(`[data-cy="expiresAt"]`).type('2026-07-07T13:38');
      cy.get(`[data-cy="expiresAt"]`).blur();
      cy.get(`[data-cy="expiresAt"]`).should('have.value', '2026-07-07T13:38');

      cy.get(`[data-cy="revokedAt"]`).type('2026-07-06T20:34');
      cy.get(`[data-cy="revokedAt"]`).blur();
      cy.get(`[data-cy="revokedAt"]`).should('have.value', '2026-07-06T20:34');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        apiAccessToken = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', apiAccessTokenPageUrlPattern);
    });
  });
});
