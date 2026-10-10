import React from 'react';
import { Link } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';

import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

const Rules = () => (
  <ProductPage wide>
    <ProductPageHeader
      headingId="rules-heading"
      dataCy="rulesHeading"
      title={<Translate contentKey="fintrackApp.rules.title">Rules</Translate>}
      subtitle={
        <Translate contentKey="fintrackApp.rules.subtitle">Automate how your transactions are normalized and classified.</Translate>
      }
    />

    <Row xs="1" md="2" className="g-3" data-cy="rulesDestinations">
      <Col>
        <ProductSection
          title={<Translate contentKey="fintrackApp.rules.description.title">Description Rules</Translate>}
          dataCy="descriptionRulesSection"
        >
          <div className="d-flex h-100 flex-column">
            <p>
              <Translate contentKey="fintrackApp.rules.description.summary">
                Standardize original descriptions from imported files before classifying them.
              </Translate>
            </p>
            <ul className="mb-4 ps-3 text-muted">
              <li>
                <Translate contentKey="fintrackApp.rules.description.original">
                  They evaluate the original description from imported files.
                </Translate>
              </li>
              <li>
                <Translate contentKey="fintrackApp.rules.description.priority">Active rules are evaluated in priority order.</Translate>
              </li>
              <li>
                <Translate contentKey="fintrackApp.rules.description.firstMatch">
                  The first matching rule determines the normalized description.
                </Translate>
              </li>
            </ul>
            <Button
              tag={Link}
              to="/description-normalization-rule"
              color="primary"
              className="mt-auto align-self-start"
              data-cy="rulesDescriptionManagerLink"
            >
              <Translate contentKey="fintrackApp.rules.description.action">Manage description rules</Translate>
            </Button>
          </div>
        </ProductSection>
      </Col>
      <Col>
        <ProductSection
          title={<Translate contentKey="fintrackApp.rules.classification.title">Classification Rules</Translate>}
          dataCy="classificationRulesSection"
        >
          <div className="d-flex h-100 flex-column">
            <p>
              <Translate contentKey="fintrackApp.rules.classification.summary">
                Assign category and tag suggestions from your transaction data.
              </Translate>
            </p>
            <ul className="mb-4 ps-3 text-muted">
              <li>
                <Translate contentKey="fintrackApp.rules.classification.attributes">
                  They evaluate transaction attributes in supported import and manual workflows.
                </Translate>
              </li>
              <li>
                <Translate contentKey="fintrackApp.rules.classification.categoryPriority">
                  Multiple rules can match; priority determines which category takes precedence.
                </Translate>
              </li>
              <li>
                <Translate contentKey="fintrackApp.rules.classification.tags">Tags from matching rules can accumulate.</Translate>
              </li>
            </ul>
            <Button
              tag={Link}
              to="/transaction-rule"
              color="primary"
              className="mt-auto align-self-start"
              data-cy="rulesClassificationManagerLink"
            >
              <Translate contentKey="fintrackApp.rules.classification.action">Manage classification rules</Translate>
            </Button>
          </div>
        </ProductSection>
      </Col>
    </Row>
  </ProductPage>
);

export default Rules;
