import { entityCreateSaveButtonSelector } from '../../support/entity';

describe('DescriptionNormalizationRule configured editor e2e test', () => {
  const pageUrl = '/description-normalization-rule';
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const createdRuleIds: number[] = [];

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  beforeEach(() => {
    createdRuleIds.length = 0;
    cy.login(username, password);
  });

  afterEach(() => {
    createdRuleIds.forEach(id => {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/description-normalization-rules/${id}`,
        failOnStatusCode: false,
      });
    });
  });

  it('creates, edits, reloads, and keeps the priority of a configured normalization rule', () => {
    const ruleName = uniqueName('dnr-configured');
    let configuredRuleId: number;
    let originalPriority: number;

    cy.intercept('POST', '/api/description-normalization-rules/configured').as('createConfiguredRule');
    cy.visit(`${pageUrl}/new`);
    cy.getEntityCreateUpdateHeading('DescriptionNormalizationRule').should('exist');
    cy.get('[data-cy="descriptionNormalizationRuleName"]').type(ruleName);
    cy.get('[data-cy="descriptionNormalizationRuleResult"]').type('Uber');
    cy.get('[data-cy="descriptionNormalizationRuleActive"]').check();
    cy.get(entityCreateSaveButtonSelector).click();
    cy.get('[data-cy="descriptionNormalizationRuleFormError"]').should('be.visible');

    cy.get('[data-cy="addDescriptionNormalizationRuleCondition"]').click();
    cy.get('[data-cy="descriptionNormalizationRuleConditionValue-0"]').type('UBER');
    cy.get('[data-cy="addDescriptionNormalizationRuleCondition"]').click();
    cy.get('[data-cy="descriptionNormalizationRuleConditionOperator-1"]').select('REGEX');
    cy.get('[data-cy="descriptionNormalizationRuleConditionValue-1"]').type('RIDE.*');
    cy.get('[data-cy="descriptionNormalizationRuleConfigurationPreview"]').should('be.visible');

    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@createConfiguredRule').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(response?.body.conditions).to.have.length(2);
      configuredRuleId = response?.body.id as number;
      originalPriority = response?.body.priority as number;
      expect(configuredRuleId).to.be.a('number');
      expect(originalPriority).to.be.a('number');
      createdRuleIds.push(configuredRuleId);
      cy.url().should('match', new RegExp(`${pageUrl}(\\?.*)?$`));

      cy.intercept('GET', `/api/description-normalization-rules/${configuredRuleId}/configured`).as('configuredRule');
      cy.visit(`${pageUrl}/${configuredRuleId}/edit`);
      cy.wait('@configuredRule').its('response.statusCode').should('eq', 200);
      cy.get('[data-cy="descriptionNormalizationRuleName"]').should('have.value', ruleName);
      cy.get('[data-cy="descriptionNormalizationRuleConditionValue-0"]').should('have.value', 'UBER');
      cy.get('[data-cy="descriptionNormalizationRuleConditionValue-1"]').should('have.value', 'RIDE.*');

      cy.get('[data-cy="descriptionNormalizationRuleConditionLogic"]').select('ANY');
      cy.get('[data-cy="descriptionNormalizationRuleConditionValue-0"]').clear();
      cy.get('[data-cy="descriptionNormalizationRuleConditionValue-0"]').type('UBER EATS');
      cy.get('[data-cy="deleteDescriptionNormalizationRuleCondition-1"]').click();
      cy.get('[data-cy="descriptionNormalizationRuleResult"]').clear();
      cy.get('[data-cy="descriptionNormalizationRuleResult"]').type('Uber food');

      cy.intercept('PUT', `/api/description-normalization-rules/${configuredRuleId}/configured`).as('updateConfiguredRule');
      cy.get(entityCreateSaveButtonSelector).click();
      cy.wait('@updateConfiguredRule').then(({ response: updateResponse }) => {
        expect(updateResponse?.statusCode).to.equal(200);
        expect(updateResponse?.body.priority).to.equal(originalPriority);
        expect(updateResponse?.body.conditionOperator).to.equal('ANY');
        expect(updateResponse?.body.conditions).to.have.length(1);
        expect(updateResponse?.body.conditions[0].value).to.equal('UBER EATS');
      });

      cy.intercept('GET', `/api/description-normalization-rules/${configuredRuleId}/configured`).as('reloadedConfiguredRule');
      cy.visit(`${pageUrl}/${configuredRuleId}/edit`);
      cy.wait('@reloadedConfiguredRule').its('response.statusCode').should('eq', 200);
      cy.get('[data-cy="descriptionNormalizationRuleConditionLogic"]').should('have.value', 'ANY');
      cy.get('[data-cy="descriptionNormalizationRuleConditionValue-0"]').should('have.value', 'UBER EATS');
      cy.get('[data-cy="descriptionNormalizationRuleConditionValue-1"]').should('not.exist');

      cy.visit(`${pageUrl}/${configuredRuleId}`);
      cy.getEntityDetailsHeading('descriptionNormalizationRule').should('exist');
    });
  });
});
