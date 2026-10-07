import React, { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { Button, ButtonGroup, Col, Input, Label, Row } from 'reactstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Translate, isNumber, translate } from 'react-jhipster';

import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { ICategory } from 'app/shared/model/category.model';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { ITag } from 'app/shared/model/tag.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { mapIdList } from 'app/shared/util/entity-utils';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { ProductValidatedField, ProductValidatedForm } from 'app/shared/ui/product-validated-form';
import { ProductTagSelector } from 'app/shared/ui/product-tag-selector';

import { getEntity, partialUpdateEntity } from './financial-transaction.reducer';
import { TransactionAccountLabel, TransactionCategory } from './transaction-presentation';

const toOptionalNumber = (value: unknown) => (value === undefined || value === null || value === '' ? undefined : Number(value));

const categoryCompatibleWithFlow = (category: ICategorySelectable, flow: keyof typeof TransactionFlow) =>
  category.categoryType === 'BOTH' || (flow === 'OUT' ? category.categoryType === 'EXPENSE' : category.categoryType === 'INCOME');

const categoryOptionLabel = (category: ICategorySelectable) =>
  [
    category.parentCategoryName,
    category.name,
    category.active === false ? `(${translate('fintrackApp.category.status.inactive')})` : undefined,
  ]
    .filter(Boolean)
    .join(' › ');

const toCategoryPresentation = (category?: ICategorySelectable | ICategory | null): ICategory | null => {
  if (!category?.name) {
    return null;
  }

  const selectableCategory = category as ICategorySelectable;
  return {
    ...category,
    parentCategory:
      'parentCategoryName' in selectableCategory && selectableCategory.parentCategoryName
        ? { id: selectableCategory.parentCategoryId ?? undefined, name: selectableCategory.parentCategoryName }
        : (category as ICategory).parentCategory,
  } as ICategory;
};

const getDefaultValues = financialTransactionEntity => ({
  ...financialTransactionEntity,
  transactionDate: financialTransactionEntity.transactionDate ? dayjs(financialTransactionEntity.transactionDate).format('YYYY-MM-DD') : '',
  postingDate: financialTransactionEntity.postingDate ? dayjs(financialTransactionEntity.postingDate).format('YYYY-MM-DD') : '',
  externalReference: financialTransactionEntity.externalReference ?? '',
  notes: financialTransactionEntity.notes ?? '',
});

export const FinancialTransactionUpdate = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams<'id'>();
  const financialTransactionEntity = useAppSelector(state => state.financialTransaction.entity);
  const loading = useAppSelector(state => state.financialTransaction.loading);
  const updating = useAppSelector(state => state.financialTransaction.updating);
  const updateSuccess = useAppSelector(state => state.financialTransaction.updateSuccess);
  const [selectedFlow, setSelectedFlow] = useState<keyof typeof TransactionFlow>('OUT');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [selectedTagIds, setSelectedTagIds] = useState<string[]>([]);
  const [selectableCategories, setSelectableCategories] = useState<ICategorySelectable[]>([]);
  const [selectableTags, setSelectableTags] = useState<ITagSelectable[]>([]);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [tagsLoadError, setTagsLoadError] = useState(false);
  const [optionalDetailsOpen, setOptionalDetailsOpen] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
  }, [dispatch, id]);

  useEffect(() => {
    if (!financialTransactionEntity.id) {
      return;
    }
    setSelectedFlow((financialTransactionEntity.flow as keyof typeof TransactionFlow) ?? 'OUT');
    setSelectedCategoryId(financialTransactionEntity.category?.id?.toString() ?? '');
    setSelectedTagIds(financialTransactionEntity.tags?.map(tag => tag.id?.toString()).filter((tagId): tagId is string => !!tagId) ?? []);
  }, [financialTransactionEntity.id]);

  useEffect(() => {
    setOptionalDetailsOpen(Boolean(financialTransactionEntity.externalReference || financialTransactionEntity.notes));
  }, [financialTransactionEntity.id]);

  useEffect(() => {
    let mounted = true;
    const categoryIds = financialTransactionEntity.category?.id === undefined ? [] : [financialTransactionEntity.category.id];
    const tagIds = financialTransactionEntity.tags?.map(tag => tag.id).filter((tagId): tagId is number => tagId !== undefined) ?? [];

    setTagsLoading(true);
    setTagsLoadError(false);
    Promise.all([getSelectableCategories(categoryIds), getSelectableTags(tagIds)])
      .then(([categories, tags]) => {
        if (mounted) {
          setSelectableCategories(categories);
          setSelectableTags(tags);
        }
      })
      .catch(() => {
        if (mounted) {
          setSelectableCategories([]);
          setSelectableTags([]);
          setTagsLoadError(true);
        }
      })
      .finally(() => {
        if (mounted) {
          setTagsLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, [financialTransactionEntity.category?.id, financialTransactionEntity.tags]);

  useEffect(() => {
    if (updateSuccess) {
      navigate(`/financial-transaction${location.search}`);
    }
  }, [location.search, navigate, updateSuccess]);

  const compatibleCategories = useMemo(
    () => selectableCategories.filter(category => categoryCompatibleWithFlow(category, selectedFlow)),
    [selectableCategories, selectedFlow],
  );
  const selectedCategory = useMemo(
    () =>
      selectedCategoryId
        ? (selectableCategories.find(category => category.id?.toString() === selectedCategoryId) ??
          (financialTransactionEntity.category?.id?.toString() === selectedCategoryId ? financialTransactionEntity.category : null))
        : null,
    [financialTransactionEntity.category, selectableCategories, selectedCategoryId],
  );
  const selectedTags = useMemo(
    () =>
      selectedTagIds
        .map(
          tagId =>
            selectableTags.find(tag => tag.id?.toString() === tagId) ??
            financialTransactionEntity.tags?.find(tag => tag.id?.toString() === tagId),
        )
        .filter((tag): tag is ITagSelectable | ITag => !!tag),
    [financialTransactionEntity.tags, selectableTags, selectedTagIds],
  );
  const defaultValues = useMemo(() => getDefaultValues(financialTransactionEntity), [financialTransactionEntity]);

  const changeFlow = (nextFlow: keyof typeof TransactionFlow) => {
    setSelectedFlow(nextFlow);
    const currentCategory = selectableCategories.find(category => category.id?.toString() === selectedCategoryId);
    if (currentCategory && !categoryCompatibleWithFlow(currentCategory, nextFlow)) {
      setSelectedCategoryId('');
    }
  };

  const saveEntity = values => {
    if (!financialTransactionEntity.id) {
      return;
    }

    dispatch(
      partialUpdateEntity({
        id: financialTransactionEntity.id,
        transactionDate: values.transactionDate ? dayjs(values.transactionDate) : undefined,
        postingDate: values.postingDate ? dayjs(values.postingDate) : null,
        description: values.description,
        amount: toOptionalNumber(values.amount),
        flow: selectedFlow,
        category: selectedCategoryId ? { id: Number(selectedCategoryId) } : null,
        tags: mapIdList(selectedTagIds),
        externalReference: values.externalReference || null,
        notes: values.notes || null,
      }),
    );
  };

  if (loading && !financialTransactionEntity.id) {
    return (
      <ProductPage>
        <p className="text-muted mb-0">
          <Translate contentKey="fintrackApp.financialTransaction.product.loading">Loading transactions…</Translate>
        </p>
      </ProductPage>
    );
  }

  return (
    <ProductPage>
      <ProductValidatedForm formKey={financialTransactionEntity.id ?? 'loading'} defaultValues={defaultValues} onSubmit={saveEntity}>
        <ProductPageHeader
          headingId="financial-transaction-edit-heading"
          dataCy="FinancialTransactionCreateUpdateHeading"
          accentColor={financialTransactionEntity.account?.color}
          accentDataCy="financialTransactionEditColorAccent"
          title={<Translate contentKey="fintrackApp.financialTransaction.product.editTitle">Edit transaction</Translate>}
          subtitle={
            financialTransactionEntity.description || financialTransactionEntity.account ? (
              <>
                {financialTransactionEntity.description ? `${financialTransactionEntity.description} · ` : null}
                <TransactionAccountLabel account={financialTransactionEntity.account} />
              </>
            ) : undefined
          }
        />

        <div className="vstack gap-3">
          <ProductSection
            title={<Translate contentKey="fintrackApp.financialTransaction.product.transaction">Transaction</Translate>}
            dataCy="financialTransactionEditTransactionSection"
          >
            <Row className="g-3">
              <Col xs="12">
                <div className="text-muted small mb-1">
                  <Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate>
                </div>
                <TransactionAccountLabel account={financialTransactionEntity.account} className="fw-semibold" />
              </Col>
              <Col xs="12">
                <ProductValidatedField
                  label={translate('fintrackApp.financialTransaction.description')}
                  id="financial-transaction-description"
                  name="description"
                  data-cy="description"
                  type="text"
                  validate={{
                    required: { value: true, message: translate('entity.validation.required') },
                    minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                    maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                  }}
                />
              </Col>
              <Col md="5">
                <div className="form-label mb-2">
                  <Translate contentKey="fintrackApp.financialTransaction.flow">Type</Translate>
                </div>
                <ButtonGroup
                  role="group"
                  aria-label={translate('fintrackApp.financialTransaction.flow')}
                  data-cy="financialTransactionFlowControl"
                >
                  <Button
                    type="button"
                    color={selectedFlow === 'OUT' ? 'primary' : 'secondary'}
                    outline={selectedFlow !== 'OUT'}
                    aria-pressed={selectedFlow === 'OUT'}
                    data-cy="financialTransactionFlowOut"
                    onClick={() => changeFlow('OUT')}
                  >
                    <Translate contentKey="fintrackApp.TransactionFlow.OUT">Expense</Translate>
                  </Button>
                  <Button
                    type="button"
                    color={selectedFlow === 'IN' ? 'primary' : 'secondary'}
                    outline={selectedFlow !== 'IN'}
                    aria-pressed={selectedFlow === 'IN'}
                    data-cy="financialTransactionFlowIn"
                    onClick={() => changeFlow('IN')}
                  >
                    <Translate contentKey="fintrackApp.TransactionFlow.IN">Income</Translate>
                  </Button>
                </ButtonGroup>
              </Col>
              <Col md="7">
                <ProductValidatedField
                  label={translate('fintrackApp.financialTransaction.amount')}
                  id="financial-transaction-amount"
                  name="amount"
                  data-cy="amount"
                  type="number"
                  step="0.01"
                  validate={{
                    required: { value: true, message: translate('entity.validation.required') },
                    min: { value: 0.01, message: translate('entity.validation.min', { min: 0.01 }) },
                    validate: value => (isNumber(value) && Number(value) > 0) || translate('entity.validation.min', { min: 0.01 }),
                  }}
                />
              </Col>
              <Col md="6">
                <ProductValidatedField
                  label={translate('fintrackApp.financialTransaction.transactionDate')}
                  id="financial-transaction-transactionDate"
                  name="transactionDate"
                  data-cy="transactionDate"
                  type="date"
                  validate={{ required: { value: true, message: translate('entity.validation.required') } }}
                />
              </Col>
              <Col md="6">
                <ProductValidatedField
                  label={translate('fintrackApp.financialTransaction.postingDate')}
                  id="financial-transaction-postingDate"
                  name="postingDate"
                  data-cy="postingDate"
                  type="date"
                />
              </Col>
            </Row>
          </ProductSection>

          <ProductSection
            title={<Translate contentKey="fintrackApp.financialTransaction.product.classification">Classification</Translate>}
            dataCy="financialTransactionEditClassificationSection"
          >
            <Row className="g-3">
              <Col md="6">
                <Label for="financial-transaction-category">
                  <Translate contentKey="fintrackApp.financialTransaction.category">Category</Translate>
                </Label>
                <Input
                  id="financial-transaction-category"
                  name="category"
                  data-cy="category"
                  type="select"
                  value={selectedCategoryId}
                  onChange={(event: React.ChangeEvent<HTMLInputElement>) => setSelectedCategoryId(event.target.value)}
                >
                  <option value="" />
                  {compatibleCategories.map(category => (
                    <option value={category.id} key={category.id}>
                      {categoryOptionLabel(category)}
                    </option>
                  ))}
                </Input>
                <div className="pt-2">
                  <TransactionCategory category={toCategoryPresentation(selectedCategory)} />
                </div>
              </Col>
              <Col md="6">
                <div className="small fw-semibold mb-2">
                  <Translate contentKey="fintrackApp.financialTransaction.tags">Tags</Translate>
                </div>
                <ProductTagSelector
                  id="financial-transaction-tags"
                  dataCy="financialTransactionTags"
                  selectedTags={selectedTags}
                  availableTags={selectableTags}
                  loading={tagsLoading}
                  error={tagsLoadError}
                  onChange={nextTags =>
                    setSelectedTagIds(nextTags.map(tag => tag.id?.toString()).filter((tagId): tagId is string => !!tagId))
                  }
                />
              </Col>
            </Row>
          </ProductSection>

          <ProductSection
            title={<Translate contentKey="fintrackApp.financialTransaction.product.moreDetails">More details</Translate>}
            dataCy="financialTransactionEditOptionalDetails"
          >
            <details open={optionalDetailsOpen} onToggle={event => setOptionalDetailsOpen(event.currentTarget.open)}>
              <summary className="small fw-semibold text-body">
                <Translate contentKey="fintrackApp.financialTransaction.product.optionalDetails">Optional details</Translate>
              </summary>
              <Row className="g-3 pt-3">
                <Col md="6">
                  <ProductValidatedField
                    label={translate('fintrackApp.financialTransaction.externalReference')}
                    id="financial-transaction-externalReference"
                    name="externalReference"
                    data-cy="externalReference"
                    type="text"
                    validate={{ maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) } }}
                  />
                </Col>
                <Col md="6">
                  <ProductValidatedField
                    label={translate('fintrackApp.financialTransaction.notes')}
                    id="financial-transaction-notes"
                    name="notes"
                    data-cy="notes"
                    type="textarea"
                    validate={{ maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) } }}
                  />
                </Col>
              </Row>
            </details>
          </ProductSection>

          <div className="d-flex flex-wrap justify-content-end gap-2 pt-1" data-cy="financialTransactionEditActions">
            <Button
              tag={Link}
              id="cancel-save"
              data-cy="entityCreateCancelButton"
              to={`/financial-transaction${location.search}`}
              replace
              color="secondary"
              outline
            >
              <Translate contentKey="entity.action.cancel">Cancel</Translate>
            </Button>
            <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
              <FontAwesomeIcon icon="save" />{' '}
              <Translate contentKey="fintrackApp.financialTransaction.product.saveChanges">Save changes</Translate>
            </Button>
          </div>
        </div>
      </ProductValidatedForm>
    </ProductPage>
  );
};

export default FinancialTransactionUpdate;
