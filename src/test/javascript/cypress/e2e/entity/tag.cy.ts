import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityCreateSaveButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('Tag product UX e2e test', () => {
  const tagPageUrl = '/tag';
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let tags: Array<{ id: number; name: string }> = [];
  let candidateId: number | undefined;

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const createTagViaApi = (name: string) =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/tags',
        body: {
          name,
          description: 'Tag product E2E fixture',
          color: '#a1b2c3',
          active: true,
        },
      })
      .then(({ body }) => {
        tags.push(body);
        return body;
      });

  beforeEach(() => {
    tags = [];
    candidateId = undefined;
    cy.login(username, password);
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');
    cy.intercept('GET', '/api/tags/*').as('tagRequest');
    cy.intercept('POST', '/api/tags').as('createTagRequest');
    cy.intercept('PATCH', '/api/tags/*').as('updateTagRequest');
    cy.intercept('DELETE', '/api/tags/*').as('deleteTagRequest');
  });

  afterEach(() => {
    if (candidateId) {
      cy.authenticatedRequest({ method: 'DELETE', url: `/api/transaction-candidates/${candidateId}`, failOnStatusCode: false });
      candidateId = undefined;
    }
    [...tags].reverse().forEach(tag => {
      cy.authenticatedRequest({ method: 'DELETE', url: `/api/tags/${tag.id}`, failOnStatusCode: false });
    });
    tags = [];
  });

  it('creates, presents, edits, deactivates/reactivates, and deletes a tag as a product catalog item', () => {
    const tagName = uniqueName('Travel');

    cy.viewport(1440, 900);
    cy.visit(tagPageUrl);
    cy.wait('@tagsRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="TagHeading"]').should('exist');
    cy.get(entityCreateButtonSelector).click();

    cy.get('[data-cy="tagColorPicker"]').should('exist');
    cy.get('[data-cy="color"]').should('exist');
    cy.get('[data-cy="tagColorControlRow"]').within(() => {
      cy.get('[data-cy="tagColorPicker"]').should('exist');
      cy.get('[data-cy="color"]').should('exist');
    });
    cy.get('[data-cy="tagColorPreview"]').should('not.exist');
    cy.get('[data-cy="name"]').clear().type(tagName);
    cy.get('[data-cy="description"]').clear().type('Trips and travel');
    cy.get('[data-cy="tagColorPicker"]').then($input => {
      const input = $input[0] as HTMLInputElement;
      const valueSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
      valueSetter?.call(input, '#112233');
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });
    cy.get('[data-cy="color"]').should('have.value', '#112233');
    cy.get('[data-cy="tagColorPreview"]').should('not.exist');
    cy.get('[data-cy="active"]').should('be.checked');
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@createTagRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
      expect(response?.body.name).to.equal(tagName);
      tags.push(response!.body);
    });

    cy.get(entityTableSelector).should('contain', tagName).and('contain', 'Trips and travel');
    cy.contains(entityTableSelector, tagName).within(() => {
      cy.get('[data-cy="tagPrimaryRow"]').within(() => {
        cy.get('[data-cy="tagColorChip"]').should('contain', tagName);
        cy.get('[data-cy="tagStatusBadge"]').should('exist');
      });
      cy.get('[data-cy="tagDescription"]').should('contain', 'Trips and travel');
      cy.get('[data-cy="entityActionsMenuToggle"]').click();
      cy.get('[data-cy="entityDetailsButton"]').click();
    });
    cy.get('[data-cy="tagDetailsHeading"]').should('exist');
    cy.get('[data-cy="tagDetailColorAccent"]').should($header => {
      expect($header.css('border-left-color')).to.match(/rgb\(17,\s*34,\s*51\)/);
    });
    cy.get('[data-cy="tagDetailAppearance"]').should('not.exist');
    cy.contains('#112233').should('not.exist');
    cy.contains('true').should('not.exist');
    cy.contains('Created At').should('not.exist');
    cy.get('[data-cy="tagDetailStatusHelpButton"]').focus();
    cy.get('[data-cy="tagDetailStatusHelpTooltip"]').should('be.visible');
    cy.get('[data-cy="tagDetailStatusHelpButton"]').blur();
    cy.viewport(1024, 768);
    cy.get('[data-cy="tagDetailStatusHelpButton"]').focus();
    cy.get('[data-cy="tagDetailStatusHelpTooltip"]').should('be.visible');
    cy.get('[data-cy="entityDetailsBackButton"]').click();
    cy.location('pathname').should('eq', tagPageUrl);

    cy.then(() => cy.visit(`/tag/${tags[0].id}/edit`));
    cy.wait('@tagRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="tagColorPicker"]').should('exist');
    cy.get('[data-cy="tagColorControlRow"]').within(() => {
      cy.get('[data-cy="tagColorPicker"]').should('exist');
      cy.get('[data-cy="color"]').should('exist');
    });
    cy.get('[data-cy="tagColorPreview"]').should('not.exist');
    cy.get('[data-cy="description"]').clear().type('Updated travel');
    cy.get('[data-cy="color"]').clear().type('#abcdef');
    cy.get('[data-cy="active"]').uncheck();
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@updateTagRequest').its('response.statusCode').should('eq', 200);

    cy.then(() => cy.visit(`/tag/${tags[0].id}`));
    cy.contains('Updated travel').should('exist');
    cy.get('[data-cy="tagDetailStatusHelpButton"]').should('exist').focus();

    cy.then(() => cy.visit(`/tag/${tags[0].id}/edit`));
    cy.get('[data-cy="active"]').check();
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@updateTagRequest').its('response.statusCode').should('eq', 200);

    cy.then(() => cy.visit(`/tag/${tags[0].id}/delete`));
    cy.get('[data-cy="tagDeleteLeafMessage"]').should('exist');
    cy.get(entityConfirmDeleteButtonSelector).click();
    cy.wait('@deleteTagRequest').its('response.statusCode').should('eq', 204);
    cy.then(() => {
      tags = [];
    });
  });

  it('shows a product-safe delete block when an active transaction workflow still references a tag', () => {
    const tagName = uniqueName('Protected tag');
    createTagViaApi(tagName).then(tag => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-candidates/manual',
        body: {
          description: 'Draft using the protected tag',
          tags: [{ id: tag.id }],
        },
      }).then(({ body }) => {
        candidateId = body.id;
      });
    });

    cy.then(() => {
      cy.visit(`/tag/${tags[0].id}/delete`);
      cy.get(entityConfirmDeleteButtonSelector).click();
      cy.wait('@deleteTagRequest').its('response.statusCode').should('eq', 400);
      cy.get('[data-cy="tagDeleteError"]').should('exist');
      cy.contains('transaction candidates.').should('not.exist');
    });
  });
});
