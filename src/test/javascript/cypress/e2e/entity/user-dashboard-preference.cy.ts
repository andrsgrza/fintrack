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

describe('UserDashboardPreference e2e test', () => {
  const userDashboardPreferencePageUrl = '/user-dashboard-preference';
  const userDashboardPreferencePageUrlPattern = new RegExp('/user-dashboard-preference(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const userDashboardPreferenceSample = {"configuration":"Li4vZmFrZS1kYXRhL2Jsb2IvaGlwc3Rlci50eHQ=","createdAt":"2026-07-07T07:47:55.918Z","updatedAt":"2026-07-07T06:57:26.427Z"};

  let userDashboardPreference;
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
      body: {"login":"z","firstName":"Gerardo","lastName":"Barrientos Mota","email":"Federico_AguileraAtencio@hotmail.com","imageUrl":"wherever","langKey":"past"},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/user-dashboard-preferences+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/user-dashboard-preferences').as('postEntityRequest');
    cy.intercept('DELETE', '/api/user-dashboard-preferences/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/users', {
      statusCode: 200,
      body: [user],
    });

  });
   */

  afterEach(() => {
    if (userDashboardPreference) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/user-dashboard-preferences/${userDashboardPreference.id}`,
      }).then(() => {
        userDashboardPreference = undefined;
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

  it('UserDashboardPreferences menu should load UserDashboardPreferences page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('user-dashboard-preference');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('UserDashboardPreference').should('exist');
    cy.url().should('match', userDashboardPreferencePageUrlPattern);
  });

  describe('UserDashboardPreference page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(userDashboardPreferencePageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create UserDashboardPreference page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/user-dashboard-preference/new$'));
        cy.getEntityCreateUpdateHeading('UserDashboardPreference');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', userDashboardPreferencePageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/user-dashboard-preferences',
          body: {
            ...userDashboardPreferenceSample,
            user: user,
          },
        }).then(({ body }) => {
          userDashboardPreference = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/user-dashboard-preferences+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [userDashboardPreference],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(userDashboardPreferencePageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(userDashboardPreferencePageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details UserDashboardPreference page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('userDashboardPreference');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', userDashboardPreferencePageUrlPattern);
      });

      it('edit button click should load edit UserDashboardPreference page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('UserDashboardPreference');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', userDashboardPreferencePageUrlPattern);
      });

      it('edit button click should load edit UserDashboardPreference page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('UserDashboardPreference');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', userDashboardPreferencePageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of UserDashboardPreference', () => {
        cy.intercept('GET', '/api/user-dashboard-preferences/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('userDashboardPreference').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', userDashboardPreferencePageUrlPattern);

        userDashboardPreference = undefined;
      });
    });
  });

  describe('new UserDashboardPreference page', () => {
    beforeEach(() => {
      cy.visit(`${userDashboardPreferencePageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('UserDashboardPreference');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of UserDashboardPreference', () => {
      cy.get(`[data-cy="configuration"]`).type('../fake-data/blob/hipster.txt');
      cy.get(`[data-cy="configuration"]`).invoke('val').should('match', new RegExp('../fake-data/blob/hipster.txt'));

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T05:02');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T05:02');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T07:06');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T07:06');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        userDashboardPreference = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', userDashboardPreferencePageUrlPattern);
    });
  });
});
