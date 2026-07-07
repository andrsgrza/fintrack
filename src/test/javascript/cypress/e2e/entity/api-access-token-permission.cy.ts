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

describe('ApiAccessTokenPermission e2e test', () => {
  const apiAccessTokenPermissionPageUrl = '/api-access-token-permission';
  const apiAccessTokenPermissionPageUrlPattern = new RegExp('/api-access-token-permission(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const apiAccessTokenPermissionSample = {"permission":"CREATE_TRANSACTIONS","createdAt":"2026-07-07T01:55:14.962Z"};

  let apiAccessTokenPermission;
  // let apiAccessToken;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/api-access-tokens',
      body: {"name":"intensely boohoo","tokenPrefix":"hairy amid","tokenHash":"better unfreeze","status":"EXPIRED","createdAt":"2026-07-07T01:22:08.983Z","updatedAt":"2026-07-07T07:02:06.868Z","lastUsedAt":"2026-07-07T10:32:51.716Z","expiresAt":"2026-07-07T16:20:58.806Z","revokedAt":"2026-07-07T00:57:27.595Z"},
    }).then(({ body }) => {
      apiAccessToken = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/api-access-token-permissions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/api-access-token-permissions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/api-access-token-permissions/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/api-access-tokens', {
      statusCode: 200,
      body: [apiAccessToken],
    });

  });
   */

  afterEach(() => {
    if (apiAccessTokenPermission) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/api-access-token-permissions/${apiAccessTokenPermission.id}`,
      }).then(() => {
        apiAccessTokenPermission = undefined;
      });
    }
  });

  /* Disabled due to incompatibility
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
   */

  it('ApiAccessTokenPermissions menu should load ApiAccessTokenPermissions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('api-access-token-permission');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('ApiAccessTokenPermission').should('exist');
    cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);
  });

  describe('ApiAccessTokenPermission page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(apiAccessTokenPermissionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create ApiAccessTokenPermission page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/api-access-token-permission/new$'));
        cy.getEntityCreateUpdateHeading('ApiAccessTokenPermission');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/api-access-token-permissions',
          body: {
            ...apiAccessTokenPermissionSample,
            apiAccessToken: apiAccessToken,
          },
        }).then(({ body }) => {
          apiAccessTokenPermission = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/api-access-token-permissions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [apiAccessTokenPermission],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(apiAccessTokenPermissionPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(apiAccessTokenPermissionPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details ApiAccessTokenPermission page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('apiAccessTokenPermission');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);
      });

      it('edit button click should load edit ApiAccessTokenPermission page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('ApiAccessTokenPermission');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);
      });

      it('edit button click should load edit ApiAccessTokenPermission page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('ApiAccessTokenPermission');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of ApiAccessTokenPermission', () => {
        cy.intercept('GET', '/api/api-access-token-permissions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('apiAccessTokenPermission').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);

        apiAccessTokenPermission = undefined;
      });
    });
  });

  describe('new ApiAccessTokenPermission page', () => {
    beforeEach(() => {
      cy.visit(`${apiAccessTokenPermissionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('ApiAccessTokenPermission');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of ApiAccessTokenPermission', () => {
      cy.get(`[data-cy="permission"]`).select('CREATE_TRANSACTIONS');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T09:36');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T09:36');

      cy.get(`[data-cy="apiAccessToken"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        apiAccessTokenPermission = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', apiAccessTokenPermissionPageUrlPattern);
    });
  });
});
