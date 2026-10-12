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

  it('manages the complete description-rule order with arrows and a real mouse drag, then reads, edits, and deletes a rule', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const rules = [
      { name: `dnr-order-alpha-${suffix}`, result: 'Alpha', active: true, value: 'ALPHA' },
      { name: `dnr-order-bravo-${suffix}`, result: 'Bravo', active: false, value: 'BRAVO' },
      { name: `dnr-order-charlie-${suffix}`, result: 'Charlie', active: true, value: 'CHARLIE' },
    ];
    const createdRules: Array<{ id: number; name: string; active: boolean }> = [];

    const createRule = rule =>
      cy
        .authenticatedRequest({
          method: 'POST',
          url: '/api/description-normalization-rules/configured',
          body: {
            name: rule.name,
            active: rule.active,
            conditionOperator: 'ALL',
            resultingDescription: rule.result,
            conditions: [{ operator: 'CONTAINS', value: rule.value, caseSensitive: false }],
          },
        })
        .then(response => {
          expect(response.status).to.equal(201);
          createdRuleIds.push(response.body.id);
          createdRules.push({ id: response.body.id, name: rule.name, active: rule.active });
        });

    const readOrder = () =>
      cy.get('[data-cy^="descriptionNormalizationRuleCard-"]').then($cards => Cypress._.map($cards, card => card.getAttribute('data-cy')));

    const keyboardDrag = (sourceId: number, direction: 'ArrowDown' | 'ArrowUp', steps: number) => {
      cy.get(`[data-cy="descriptionNormalizationRuleDragHandle-${sourceId}"]`).scrollIntoView();
      cy.get(`[data-cy="descriptionNormalizationRuleDragHandle-${sourceId}"]`).focus();
      cy.realPress('Space');
      for (let index = 0; index < steps; index += 1) {
        cy.realPress(direction);
      }
      cy.realPress('Space');
    };

    const mouseDrag = (sourceId: number, targetId: number) => {
      const sourceHandle = `[data-cy="descriptionNormalizationRuleDragHandle-${sourceId}"]`;
      const targetCard = `[data-cy="descriptionNormalizationRuleCard-${targetId}"]`;

      // Start with the pointer over the activator. This is a native CDP mouse
      // sequence via cypress-real-events, not an application-level event.
      cy.get(sourceHandle).realMouseMove(0, 0, {
        position: 'center',
        scrollBehavior: false,
      });
      cy.get(sourceHandle).realMouseDown({
        position: 'center',
        scrollBehavior: false,
      });
      cy.get(sourceHandle).realMouseMove(12, 0, {
        keepMouseDownButton: 'left',
        position: 'center',
        scrollBehavior: false,
      });
      cy.get(targetCard).realMouseMove(0, 0, {
        keepMouseDownButton: 'left',
        position: 'center',
        scrollBehavior: false,
      });
      cy.get(targetCard).realMouseUp({
        position: 'center',
        scrollBehavior: false,
      });
    };

    cy.viewport(1200, 1200);
    createRule(rules[0])
      .then(() => createRule(rules[1]))
      .then(() => createRule(rules[2]))
      .then(() => {
        cy.visit('/rules');
        cy.get('[data-cy="rulesDescriptionManagerLink"]').click();
        cy.url().should('include', pageUrl);
        cy.get(`[data-cy="descriptionNormalizationRuleCard-${createdRules[0].id}"]`).should('contain', rules[0].name);
        cy.get(`[data-cy="descriptionNormalizationRuleCard-${createdRules[1].id}"]`).should('contain', rules[1].name);

        readOrder().then(orderBeforeArrow => {
          const bravoCard = `descriptionNormalizationRuleCard-${createdRules[1].id}`;
          const bravoIndex = orderBeforeArrow.indexOf(bravoCard);
          const expectedOrder = [...orderBeforeArrow];
          [expectedOrder[bravoIndex - 1], expectedOrder[bravoIndex]] = [expectedOrder[bravoIndex], expectedOrder[bravoIndex - 1]];

          cy.intercept('PUT', '/api/description-normalization-rules/reorder').as('arrowReorder');
          cy.get(`[data-cy="descriptionNormalizationRuleMoveUp-${createdRules[1].id}"]`).click();
          cy.wait('@arrowReorder').its('response.statusCode').should('eq', 200);
          cy.reload();
          readOrder().should('deep.equal', expectedOrder);
        });

        readOrder().then(orderBeforeDrag => {
          const sourceCard = `descriptionNormalizationRuleCard-${createdRules[0].id}`;
          const targetCard = `descriptionNormalizationRuleCard-${createdRules[1].id}`;
          const sourceIndex = orderBeforeDrag.indexOf(sourceCard);
          const targetIndex = orderBeforeDrag.indexOf(targetCard);
          const expectedOrder = [...orderBeforeDrag];
          const [source] = expectedOrder.splice(sourceIndex, 1);
          expectedOrder.splice(targetIndex, 0, source);

          cy.intercept('PUT', '/api/description-normalization-rules/reorder').as('keyboardDragReorder');
          keyboardDrag(createdRules[0].id, sourceIndex < targetIndex ? 'ArrowDown' : 'ArrowUp', Math.abs(targetIndex - sourceIndex));
          cy.wait('@keyboardDragReorder').then(({ request, response }) => {
            expect(response?.statusCode).to.equal(200);
            expect(request.body.orderedIds).to.deep.equal(
              expectedOrder.map(card => Number(card.replace('descriptionNormalizationRuleCard-', ''))),
            );
          });
          cy.reload();
          readOrder().should('deep.equal', expectedOrder);
        });

        readOrder().then(orderBeforeDrag => {
          const sourceCard = `descriptionNormalizationRuleCard-${createdRules[0].id}`;
          const targetCard = `descriptionNormalizationRuleCard-${createdRules[1].id}`;
          const sourceIndex = orderBeforeDrag.indexOf(sourceCard);
          const targetIndex = orderBeforeDrag.indexOf(targetCard);
          const expectedOrder = [...orderBeforeDrag];
          const [source] = expectedOrder.splice(sourceIndex, 1);
          expectedOrder.splice(targetIndex, 0, source);

          cy.intercept('PUT', '/api/description-normalization-rules/reorder').as('mouseDragReorder');
          mouseDrag(createdRules[0].id, createdRules[1].id);
          cy.wait('@mouseDragReorder').then(({ request, response }) => {
            expect(response?.statusCode).to.equal(200);
            expect(request.body.orderedIds).to.deep.equal(
              expectedOrder.map(card => Number(card.replace('descriptionNormalizationRuleCard-', ''))),
            );
          });
          readOrder().should('deep.equal', expectedOrder);
          cy.reload();
          readOrder().should('deep.equal', expectedOrder);
        });

        cy.contains(`[data-cy="descriptionNormalizationRuleCard-${createdRules[2].id}"] a`, rules[2].name).as('charlieRuleLink');
        cy.get('@charlieRuleLink').click();
        cy.get('[data-cy="descriptionNormalizationRuleDetailsHeading"]').should('contain', rules[2].name);
        cy.get('[data-cy="descriptionNormalizationRuleDetailConditions"]').should('contain', rules[2].value);
        cy.get('[data-cy="descriptionNormalizationRuleDetailEdit"]').click();
        cy.get('[data-cy="descriptionNormalizationRuleResult"]').clear();
        cy.get('[data-cy="descriptionNormalizationRuleResult"]').type('Charlie normalized');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.get('[data-cy="descriptionNormalizationRuleDetailsHeading"]').should('contain', rules[2].name);
        cy.contains('Charlie normalized').should('exist');

        cy.get('[href$="/delete"]').click();
        cy.get('[data-cy="descriptionNormalizationRuleDeletePrecedence"]').should('be.visible');
        cy.intercept('DELETE', `/api/description-normalization-rules/${createdRules[2].id}`).as('deleteRule');
        cy.get('[data-cy="entityConfirmDeleteButton"]').click();
        cy.wait('@deleteRule').its('response.statusCode').should('eq', 204);
        cy.url().should('match', new RegExp(`${pageUrl}(\\?.*)?$`));
        cy.get(`[data-cy="descriptionNormalizationRuleCard-${createdRules[2].id}"]`).should('not.exist');
      });
  });
});
