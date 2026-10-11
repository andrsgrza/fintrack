import { entityCreateButtonSelector, entityCreateSaveButtonSelector } from '../../support/entity';

describe('TransactionRule configured workflow e2e test', () => {
  const transactionRulePageUrl = '/transaction-rule';
  const transactionRulePageUrlPattern = new RegExp('/transaction-rule(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  interface E2EEntity {
    id: number;
    name: string;
    [key: string]: unknown;
  }

  let expenseCategory: E2EEntity;
  let incomeCategory: E2EEntity;
  let bothCategory: E2EEntity;
  let tag: E2EEntity;
  let secondTag: E2EEntity;
  const createdRuleIds: number[] = [];

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const createCategory = (categoryType: 'EXPENSE' | 'INCOME' | 'BOTH', namePrefix: string) =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/categories',
        body: {
          name: uniqueName(namePrefix),
          description: 'TransactionRule configured E2E category',
          categoryType,
          color: '#a1b2c3',
          active: true,
        },
      })
      .then(({ body }) => body);

  const createTag = (namePrefix = 'tr-tag') =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/tags',
        body: {
          name: uniqueName(namePrefix),
          description: 'TransactionRule configured E2E tag',
          color: '#d4a1b2',
          active: true,
        },
      })
      .then(({ body }) => body);

  const createConfiguredRule = (name: string, resultingCategory: E2EEntity, flowValue: 'IN' | 'OUT') =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-rules/configured',
        body: {
          name,
          description: 'Configured E2E seed rule',
          conditionLogic: 'ALL',
          active: true,
          resultingCategory: { id: resultingCategory.id },
          resultingTags: [],
          conditions: [
            {
              field: 'FLOW',
              operator: 'EQUALS',
              value: flowValue,
              secondValue: null,
              caseSensitive: false,
            },
            {
              field: 'DESCRIPTION',
              operator: 'CONTAINS',
              value: 'Coffee',
              secondValue: null,
              caseSensitive: false,
            },
          ],
        },
      })
      .then(({ body }) => {
        createdRuleIds.push(body.id);
        return body;
      });

  const visitList = () => {
    cy.intercept('GET', '/api/transaction-rules+(?*|)').as('rulesRequest');
    cy.visit(transactionRulePageUrl);
    cy.wait('@rulesRequest').its('response.statusCode').should('eq', 200);
    cy.getEntityHeading('TransactionRule').should('exist');
  };

  const visitCreate = () => {
    cy.intercept('GET', '/api/categories/selectable*').as('categoriesRequest');
    cy.intercept('GET', '/api/tags/selectable*').as('tagsRequest');
    visitList();
    cy.get(entityCreateButtonSelector).click();
    cy.url().should('match', new RegExp('/transaction-rule/new$'));
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);
    cy.getEntityCreateUpdateHeading('TransactionRule').should('exist');
  };

  const selectCategory = (category: E2EEntity) => {
    cy.get('[data-cy="transactionRuleCategorySelect"]').click();
    cy.contains('[data-cy="transactionRuleCategoryOption"]', category.name).click();
  };

  const selectTag = (selectedTag: E2EEntity) => {
    cy.get('[data-cy="transactionRuleResultTagsAdd"]').click();
    cy.contains('[data-cy="transactionRuleResultTagsOption"]', selectedTag.name).click();
  };

  const setConditionLogic = (logic: 'ALL' | 'ANY') => {
    cy.get(`[data-cy="conditionLogic${logic === 'ALL' ? 'All' : 'Any'}"]`).check();
    cy.get(`[data-cy="conditionLogic${logic === 'ALL' ? 'All' : 'Any'}"]`).should('be.checked');
  };

  const assertAnyDisabled = () => {
    cy.get('[data-cy="conditionLogicAny"]').should('be.disabled');
  };

  const assertAnyEnabled = () => {
    cy.get('[data-cy="conditionLogicAny"]').should('not.be.disabled');
  };

  const addDescriptionContainsCondition = (value: string) => {
    cy.get('[data-cy="addConditionButton"]').click();
    cy.get('[data-cy="embeddedConditionForm"]').within(() => {
      cy.get('[data-cy="field"]').select('DESCRIPTION');
      cy.get('[data-cy="operator"]').select('CONTAINS');
      cy.get('[data-cy="value"]').clear();
      cy.get('[data-cy="value"]').type(value);
      cy.get('[data-cy="conditionSaveButton"]').click();
    });
    cy.get('[data-cy="conditionRow"][data-condition-field="DESCRIPTION"][data-condition-operator="CONTAINS"]')
      .should('have.attr', 'data-condition-value', value)
      .should('be.visible');
  };

  const addAmountBetweenCondition = (minimum: string, maximum: string) => {
    cy.get('[data-cy="addConditionButton"]').click();
    cy.get('[data-cy="embeddedConditionForm"]').within(() => {
      cy.get('[data-cy="field"]').select('AMOUNT');
      cy.get('[data-cy="operator"]').select('BETWEEN');
      cy.get('[data-cy="value"]').clear();
      cy.get('[data-cy="value"]').type(minimum);
      cy.get('[data-cy="secondValue"]').clear();
      cy.get('[data-cy="secondValue"]').type(maximum);
      cy.get('[data-cy="conditionSaveButton"]').click();
    });
    cy.get('[data-cy="conditionRow"][data-condition-field="AMOUNT"][data-condition-operator="BETWEEN"]')
      .should('have.attr', 'data-condition-value', minimum)
      .should('be.visible');
  };

  const assertRequiredFlow = (label: 'Expense' | 'Income') => {
    const flowValue = label === 'Expense' ? 'OUT' : 'IN';
    cy.get(
      `[data-cy="conditionRow"][data-condition-field="FLOW"][data-condition-operator="EQUALS"][data-condition-value="${flowValue}"]`,
    ).should('be.visible');
    cy.get('[data-cy="lockedFlowConditionHelp"]').should('be.visible');
  };

  const assertNoRequiredFlow = () => {
    cy.get('[data-cy="conditionRow"][data-condition-field="FLOW"]').should('not.exist');
    cy.get('[data-cy="lockedFlowConditionHelp"]').should('not.exist');
  };

  const saveRule = (alias: string) => {
    cy.intercept('POST', '/api/transaction-rules/configured').as(alias);
    cy.get(entityCreateSaveButtonSelector).should('not.be.disabled').click();
    cy.wait(`@${alias}`).then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      createdRuleIds.push(response?.body.id);
    });
    cy.url().should('match', transactionRulePageUrlPattern);
  };

  beforeEach(() => {
    cy.login(username, password);
    createdRuleIds.length = 0;

    createCategory('EXPENSE', 'tr-expense').then(body => {
      expenseCategory = body;
    });
    createCategory('INCOME', 'tr-income').then(body => {
      incomeCategory = body;
    });
    createCategory('BOTH', 'tr-both').then(body => {
      bothCategory = body;
    });
    createTag().then(body => {
      tag = body;
    });
    createTag('tr-tag-secondary').then(body => {
      secondTag = body;
    });
  });

  afterEach(() => {
    createdRuleIds.forEach(ruleId => {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${ruleId}`,
        failOnStatusCode: false,
      });
    });

    [expenseCategory, incomeCategory, bothCategory].forEach(category => {
      if (category?.id) {
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/categories/${category.id}`,
          failOnStatusCode: false,
        });
      }
    });

    [tag, secondTag].forEach(createdTag => {
      if (createdTag?.id) {
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/tags/${createdTag.id}`,
          failOnStatusCode: false,
        });
      }
    });
  });

  it('creates an EXPENSE rule through the configured product flow', () => {
    const ruleName = uniqueName('tr-expense-rule');

    visitCreate();
    cy.get('[data-cy="name"]').clear();
    cy.get('[data-cy="name"]').type(ruleName);
    selectCategory(expenseCategory);
    assertRequiredFlow('Expense');
    assertAnyDisabled();
    addDescriptionContainsCondition('Uber');
    addAmountBetweenCondition('20', '500');
    selectTag(tag);
    selectTag(secondTag);
    saveRule('createExpenseRule');

    cy.then(() =>
      cy.authenticatedRequest({
        method: 'GET',
        url: `/api/transaction-rules/${createdRuleIds[0]}/configured`,
      }),
    ).then(({ body }) => {
      expect(body.resultingCategory.id).to.equal(expenseCategory.id);
      expect((body.resultingTags as E2EEntity[]).map(createdTag => createdTag.id)).to.have.members([tag.id, secondTag.id]);
      expect(body.conditions.some(condition => condition.field === 'AMOUNT' && condition.operator === 'BETWEEN')).to.equal(true);
      expect(body.conditions.some(condition => condition.field === 'DESCRIPTION' && condition.value === 'Uber')).to.equal(true);
    });

    cy.contains('a', ruleName).should('be.visible').click();
    cy.getEntityDetailsHeading('transactionRule').should('exist');
    cy.contains(ruleName).should('be.visible');
    cy.get('[data-cy="transactionRuleDetailConditions"]')
      .should('contain', 'Descripción contiene "Uber"')
      .and('contain', 'Monto entre 20 y 500');
    cy.get('[data-cy="transactionRuleDetailResults"]')
      .should('contain', expenseCategory.name)
      .and('contain', tag.name)
      .and('contain', secondTag.name);
    cy.get('[data-cy="transactionRuleDetailMultiRuleExplanation"]')
      .should('contain', 'primera categoría elegible')
      .and('contain', 'sin duplicados');
    cy.get('[data-cy="addConditionButton"]').should('not.exist');
    cy.get('[data-cy="conditionsTechnicalEditorNote"]').should('not.exist');
  });

  it('creates an INCOME rule through the configured product flow', () => {
    const ruleName = uniqueName('tr-income-rule');

    visitCreate();
    cy.get('[data-cy="name"]').clear();
    cy.get('[data-cy="name"]').type(ruleName);
    selectCategory(incomeCategory);
    assertRequiredFlow('Income');
    assertAnyDisabled();
    addDescriptionContainsCondition('Refund');
    saveRule('createIncomeRule');

    cy.contains('a', ruleName).should('be.visible');
  });

  it('forces ALL when ANY is selected before choosing an EXPENSE category', () => {
    visitCreate();
    setConditionLogic('ANY');

    selectCategory(expenseCategory);

    cy.get('[data-cy="conditionLogicAll"]').should('be.checked');
    assertAnyDisabled();
    assertRequiredFlow('Expense');
  });

  it('allows ANY for BOTH category without required Flow', () => {
    const ruleName = uniqueName('tr-both-rule');

    visitCreate();
    cy.get('[data-cy="name"]').clear();
    cy.get('[data-cy="name"]').type(ruleName);
    selectCategory(bothCategory);
    assertNoRequiredFlow();
    assertAnyEnabled();
    setConditionLogic('ANY');
    addDescriptionContainsCondition('General');
    saveRule('createBothRule');

    cy.contains('a', ruleName).should('be.visible');
  });

  it('allows ANY for tag-only rules without required Flow', () => {
    const ruleName = uniqueName('tr-tag-rule');

    visitCreate();
    cy.get('[data-cy="name"]').clear();
    cy.get('[data-cy="name"]').type(ruleName);
    selectTag(tag);
    assertNoRequiredFlow();
    assertAnyEnabled();
    setConditionLogic('ANY');
    addDescriptionContainsCondition('Tagged');
    saveRule('createTagOnlyRule');

    cy.contains('a', ruleName).should('be.visible');
  });

  it('edits a configured rule and updates required Flow when category changes from EXPENSE to INCOME', () => {
    const ruleName = uniqueName('tr-edit-rule');
    let configuredRuleId: number;

    createConfiguredRule(ruleName, expenseCategory, 'OUT').then(rule => {
      configuredRuleId = rule.id;
      cy.intercept('GET', `/api/transaction-rules/${rule.id}/configured`).as('configuredRuleRequest');
      cy.visit(`/transaction-rule/${rule.id}/edit`);
      cy.wait('@configuredRuleRequest').its('response.statusCode').should('eq', 200);
    });

    cy.getEntityCreateUpdateHeading('TransactionRule').should('exist');
    cy.get('[data-cy="name"]').should('have.value', ruleName);
    cy.get('[data-cy="conditionRow"][data-condition-field="DESCRIPTION"][data-condition-operator="CONTAINS"]')
      .should('have.attr', 'data-condition-value', 'Coffee')
      .should('be.visible');
    assertRequiredFlow('Expense');

    cy.get('[data-cy="conditionRow"][data-condition-field="DESCRIPTION"]').within(() => {
      cy.get('[data-cy="editConditionButton"]').click();
    });
    cy.get('[data-cy="embeddedConditionForm"]').within(() => {
      cy.get('[data-cy="operator"]').select('STARTS_WITH');
      cy.get('[data-cy="value"]').clear();
      cy.get('[data-cy="value"]').type('Coffee shop');
      cy.get('[data-cy="conditionSaveButton"]').click();
    });
    cy.get('[data-cy="conditionRow"][data-condition-field="DESCRIPTION"][data-condition-operator="STARTS_WITH"]')
      .should('have.attr', 'data-condition-value', 'Coffee shop')
      .should('be.visible');
    addAmountBetweenCondition('10', '30');
    cy.get('[data-cy="conditionRow"][data-condition-field="AMOUNT"]').within(() => {
      cy.get('[data-cy="deleteConditionButton"]').click();
    });
    cy.get('[data-cy="conditionRow"][data-condition-field="AMOUNT"]').should('not.exist');

    selectCategory(incomeCategory);

    assertRequiredFlow('Income');
    cy.get('[data-cy="conditionRow"][data-condition-field="FLOW"][data-condition-value="OUT"]').should('not.exist');
    cy.intercept('PUT', '/api/transaction-rules/*/configured').as('updateConfiguredRule');
    cy.get(entityCreateSaveButtonSelector).should('not.be.disabled').click();
    cy.wait('@updateConfiguredRule').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.resultingCategory.id).to.equal(incomeCategory.id);
      expect(response?.body.conditions.some(condition => condition.field === 'FLOW' && condition.value === 'IN')).to.equal(true);
      expect(
        response?.body.conditions.some(condition => condition.field === 'DESCRIPTION' && condition.operator === 'STARTS_WITH'),
      ).to.equal(true);
      expect(response?.body.conditions.some(condition => condition.field === 'AMOUNT')).to.equal(false);
    });
    cy.then(() => {
      cy.url().should('match', new RegExp(`/transaction-rule/${configuredRuleId}$`));
      cy.contains(ruleName).should('be.visible');
    });
  });

  it('keeps the product detail read-only while the technical condition route remains available', () => {
    const ruleName = uniqueName('tr-debug-rule');

    createConfiguredRule(ruleName, expenseCategory, 'OUT').then(() => {
      visitList();
      cy.contains('[data-cy^="transactionRuleCard-"] a', ruleName).click();
      cy.get('[data-cy="transactionRuleDetailConditions"]').should('be.visible');
      cy.get('[data-cy="addConditionButton"]').should('not.exist');
      cy.get('[data-cy="conditionsTechnicalEditorNote"]').should('not.exist');

      cy.visit('/transaction-rule-condition');
      cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    });
  });

  it('manages complete classification-rule priority with arrows and keyboard drag-and-drop', () => {
    const suffix = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const rules = [
      {
        name: `tr-order-category-and-tag-${suffix}`,
        active: true,
        conditionLogic: 'ALL',
        resultingCategory: expenseCategory,
        resultingTags: [tag],
        conditions: [
          { field: 'FLOW', operator: 'EQUALS', value: 'OUT', secondValue: null, caseSensitive: false },
          { field: 'DESCRIPTION', operator: 'CONTAINS', value: 'Coffee', secondValue: null, caseSensitive: false },
        ],
      },
      {
        name: `tr-order-inactive-tags-${suffix}`,
        active: false,
        conditionLogic: 'ANY',
        resultingCategory: null,
        resultingTags: [tag],
        conditions: [
          { field: 'DESCRIPTION', operator: 'CONTAINS', value: 'Rent', secondValue: null, caseSensitive: true },
          { field: 'AMOUNT', operator: 'BETWEEN', value: '20', secondValue: '500', caseSensitive: false },
        ],
      },
      {
        name: `tr-order-category-${suffix}`,
        active: true,
        conditionLogic: 'ANY',
        resultingCategory: bothCategory,
        resultingTags: [],
        conditions: [{ field: 'EXTERNAL_REFERENCE', operator: 'CONTAINS', value: 'ref', secondValue: null, caseSensitive: false }],
      },
    ];
    const createdRules: Array<{ id: number; name: string; active: boolean }> = [];

    const createRule = rule =>
      cy
        .authenticatedRequest({
          method: 'POST',
          url: '/api/transaction-rules/configured',
          body: {
            name: rule.name,
            description: 'Classification manager E2E rule',
            conditionLogic: rule.conditionLogic,
            active: rule.active,
            resultingCategory: rule.resultingCategory ? { id: rule.resultingCategory.id } : null,
            resultingTags: rule.resultingTags.map(resultingTag => ({ id: resultingTag.id })),
            conditions: rule.conditions,
          },
        })
        .then(response => {
          expect(response.status).to.equal(201);
          createdRuleIds.push(response.body.id);
          createdRules.push({ id: response.body.id, name: rule.name, active: rule.active });
        });

    const readOrder = () =>
      cy.get('[data-cy^="transactionRuleCard-"]').then($cards => Cypress._.map($cards, card => card.getAttribute('data-cy')));

    const keyboardDrag = (sourceId: number, direction: 'ArrowDown' | 'ArrowUp', steps: number) => {
      cy.get(`[data-cy="transactionRuleDragHandle-${sourceId}"]`).scrollIntoView();
      cy.get(`[data-cy="transactionRuleDragHandle-${sourceId}"]`).focus();
      cy.realPress('Space');
      for (let index = 0; index < steps; index += 1) {
        cy.realPress(direction);
      }
      cy.realPress('Space');
    };

    createRule(rules[0])
      .then(() => createRule(rules[1]))
      .then(() => createRule(rules[2]))
      .then(() => {
        cy.visit('/rules');
        cy.get('[data-cy="rulesClassificationManagerLink"]').click();
        cy.url().should('include', transactionRulePageUrl);

        cy.get(`[data-cy="transactionRuleCard-${createdRules[0].id}"]`)
          .should('contain', 'Todas las condiciones')
          .and('contain', 'Coffee')
          .and('contain', expenseCategory.name)
          .and('contain', tag.name);
        cy.get(`[data-cy="transactionRuleCard-${createdRules[1].id}"]`)
          .should('contain', 'Inactiva')
          .and('contain', 'Cualquier condición')
          .and('contain', 'Rent');

        readOrder().then(orderBeforeArrow => {
          const inactiveCard = `transactionRuleCard-${createdRules[1].id}`;
          const inactiveIndex = orderBeforeArrow.indexOf(inactiveCard);
          expect(inactiveIndex).to.be.greaterThan(0);
          const expectedOrder = [...orderBeforeArrow];
          [expectedOrder[inactiveIndex - 1], expectedOrder[inactiveIndex]] = [
            expectedOrder[inactiveIndex],
            expectedOrder[inactiveIndex - 1],
          ];

          cy.intercept('PUT', '/api/transaction-rules/reorder').as('arrowReorder');
          cy.get(`[data-cy="transactionRuleMoveUp-${createdRules[1].id}"]`).click();
          cy.wait('@arrowReorder').then(({ request, response }) => {
            expect(response?.statusCode).to.equal(200);
            expect(request.body.orderedIds).to.deep.equal(expectedOrder.map(card => Number(card.replace('transactionRuleCard-', ''))));
          });
          cy.reload();
          readOrder().should('deep.equal', expectedOrder);
        });

        readOrder().then(orderBeforeDrag => {
          const sourceCard = `transactionRuleCard-${createdRules[0].id}`;
          const targetCard = `transactionRuleCard-${createdRules[1].id}`;
          const sourceIndex = orderBeforeDrag.indexOf(sourceCard);
          const targetIndex = orderBeforeDrag.indexOf(targetCard);
          const expectedOrder = [...orderBeforeDrag];
          const [source] = expectedOrder.splice(sourceIndex, 1);
          expectedOrder.splice(targetIndex, 0, source);

          cy.intercept('PUT', '/api/transaction-rules/reorder').as('keyboardDragReorder');
          keyboardDrag(createdRules[0].id, sourceIndex < targetIndex ? 'ArrowDown' : 'ArrowUp', Math.abs(targetIndex - sourceIndex));
          cy.wait('@keyboardDragReorder').then(({ request, response }) => {
            expect(response?.statusCode).to.equal(200);
            expect(request.body.orderedIds).to.deep.equal(expectedOrder.map(card => Number(card.replace('transactionRuleCard-', ''))));
          });
          cy.reload();
          readOrder().should('deep.equal', expectedOrder);
        });

        cy.contains(`[data-cy="transactionRuleCard-${createdRules[2].id}"] a`, rules[2].name).click();
        cy.getEntityDetailsHeading('transactionRule').should('exist');
        cy.contains(rules[2].name).should('be.visible');
        cy.get('[data-cy="transactionRuleDetailConditions"]').should('be.visible');
        cy.get('[data-cy="conditionsTechnicalEditorNote"]').should('not.exist');
        cy.get('[href$="/edit"]').click();
        cy.getEntityCreateUpdateHeading('TransactionRule').should('exist');
      });
  });

  it('deletes a configured rule through product confirmation and returns to the manager', () => {
    const ruleName = uniqueName('tr-delete-rule');

    createConfiguredRule(ruleName, expenseCategory, 'OUT').then(rule => {
      cy.visit(`/transaction-rule/${rule.id}`);
      cy.getEntityDetailsHeading('transactionRule').should('exist');
      cy.get('[data-cy="entityDeleteButton"]').click();
      cy.get('[data-cy="transactionRuleDeleteDialogHeading"]').should('be.visible');
      cy.contains(/Sólo afecta sugerencias futuras/i).should('be.visible');
      cy.intercept('DELETE', `/api/transaction-rules/${rule.id}`).as('deleteRule');
      cy.get('[data-cy="entityConfirmDeleteButton"]').click();
      cy.wait('@deleteRule').its('response.statusCode').should('eq', 204);
      cy.url().should('match', transactionRulePageUrlPattern);
      cy.contains(ruleName).should('not.exist');
    });
  });
});
