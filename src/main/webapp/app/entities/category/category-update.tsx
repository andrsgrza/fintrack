import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Row } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { CategoryType } from 'app/shared/model/enumerations/category-type.model';
import { HexColorControl, isHexColor } from 'app/shared/ui/hex-color-control';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { CompactProductStatusControl } from 'app/shared/ui/product-status-control';
import { ProductValidatedField, ProductValidatedForm } from 'app/shared/ui/product-validated-form';
import { createEntity, getEntity, partialUpdateEntity, reset } from './category.reducer';
import { getCategoryPathLabel } from './category-presentation';

const categoryErrorKey = (result: unknown) => {
  const error = (result as { error?: { message?: string; response?: { data?: { detail?: string } } } }).error;
  const detail = `${error?.response?.data?.detail ?? ''} ${error?.message ?? ''}`.toLowerCase();
  if (detail.includes('already exists')) return 'fintrackApp.category.errors.duplicate';
  if (detail.includes('parent category cannot be changed')) return 'fintrackApp.category.errors.parentImmutable';
  if (detail.includes('child category type must match parent')) return 'fintrackApp.category.errors.parentType';
  if (detail.includes('type cannot be changed while category is in use')) return 'fintrackApp.category.errors.typeInUse';
  return 'fintrackApp.category.errors.saveFailed';
};

export const CategoryUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const categories = useAppSelector(state => state.category.entities);
  const categoryEntity = useAppSelector(state => state.category.entity);
  const loading = useAppSelector(state => state.category.loading);
  const updating = useAppSelector(state => state.category.updating);
  const updateSuccess = useAppSelector(state => state.category.updateSuccess);
  const categoryTypeValues = Object.keys(CategoryType);
  const [selectedCategoryType, setSelectedCategoryType] = useState<keyof typeof CategoryType>('EXPENSE');
  const [colorValue, setColorValue] = useState('');
  const [active, setActive] = useState(true);
  const [saveErrorKey, setSaveErrorKey] = useState<string | null>(null);

  const hasParentCategory = Boolean(categoryEntity?.parentCategory?.id);
  const isCategoryTypeReadOnly = !isNew && hasParentCategory;
  const parentCategoryOptions = categories.filter(
    otherEntity => (!categoryEntity?.id || otherEntity.id !== categoryEntity.id) && otherEntity.categoryType === selectedCategoryType,
  );

  const handleClose = () => {
    navigate('/category');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getCategories({}));
  }, []);

  useEffect(() => {
    if (!isNew && categoryEntity?.categoryType) {
      setSelectedCategoryType(categoryEntity.categoryType);
    }
  }, [categoryEntity?.categoryType, isNew]);

  useEffect(() => {
    setColorValue(isHexColor(categoryEntity?.color) ? categoryEntity.color! : '');
  }, [categoryEntity?.color]);

  useEffect(() => {
    setActive(isNew ? true : categoryEntity.active !== false);
  }, [categoryEntity.active, isNew]);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    setSaveErrorKey(null);
    if (isNew) {
      const entity = {
        name: values.name,
        description: values.description || null,
        categoryType: values.categoryType,
        color: colorValue || null,
        active: values.active ?? true,
        parentCategory: values.parentCategory ? categories.find(it => it.id.toString() === values.parentCategory?.toString()) : null,
      };
      dispatch(createEntity(entity)).then(result => {
        if (result.type.endsWith('/rejected')) {
          setSaveErrorKey(categoryErrorKey(result));
        }
      });
    } else {
      const entity = {
        id: categoryEntity.id,
        name: values.name,
        description: values.description || null,
        ...(!isCategoryTypeReadOnly ? { categoryType: values.categoryType } : {}),
        color: colorValue || null,
        active: values.active,
      };
      dispatch(partialUpdateEntity(entity)).then(result => {
        if (result.type.endsWith('/rejected')) {
          setSaveErrorKey(categoryErrorKey(result));
        }
      });
    }
  };

  const defaultValues = useMemo(
    () =>
      isNew
        ? {
            categoryType: 'EXPENSE',
            active: true,
          }
        : {
            ...categoryEntity,
          },
    [categoryEntity, isNew],
  );

  return (
    <ProductPage>
      {loading ? (
        <p>Loading...</p>
      ) : (
        <ProductValidatedForm defaultValues={defaultValues} onSubmit={saveEntity} formKey={categoryEntity.id ?? 'new'}>
          <ProductPageHeader
            headingId="fintrackApp.category.home.createOrEditLabel"
            dataCy="CategoryCreateUpdateHeading"
            title={<Translate contentKey="fintrackApp.category.home.createOrEditLabel">Create or edit a Category</Translate>}
            metadata={
              <CompactProductStatusControl
                id="category-active"
                name="active"
                active={active}
                onActiveChange={setActive}
                label={translate('fintrackApp.category.status.label')}
                activeLabel={translate('fintrackApp.category.status.active')}
                inactiveLabel={translate('fintrackApp.category.status.inactive')}
                help={
                  <Translate contentKey="fintrackApp.category.status.help">
                    Inactive categories are kept for history and cannot be newly assigned until reactivated.
                  </Translate>
                }
                dataCyPrefix="category"
              />
            }
            actions={
              <Button
                tag={Link}
                id="cancel-save"
                data-cy="entityCreateCancelButton"
                to="/category"
                replace
                color="secondary"
                outline
                size="sm"
              >
                <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
              </Button>
            }
          />
          {saveErrorKey ? (
            <Alert color="danger" fade={false} data-cy="categorySaveError" data-testid="categorySaveError">
              <Translate contentKey={saveErrorKey}>The category could not be saved. Review its values and try again.</Translate>
            </Alert>
          ) : null}
          <div className="vstack gap-3">
            <ProductSection
              title={<Translate contentKey="fintrackApp.category.basicInformation">Basic information</Translate>}
              dataCy="categoryBasicInformationSection"
            >
              <ProductValidatedField
                label={translate('fintrackApp.category.name')}
                id="category-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 80, message: translate('entity.validation.maxlength', { max: 80 }) },
                }}
              />
              <ProductValidatedField
                label={translate('fintrackApp.category.description')}
                id="category-description"
                name="description"
                data-cy="description"
                type="textarea"
                validate={{
                  maxLength: { value: 300, message: translate('entity.validation.maxlength', { max: 300 }) },
                }}
              />
            </ProductSection>
            <ProductSection
              title={<Translate contentKey="fintrackApp.category.classification">Classification</Translate>}
              dataCy="categoryClassificationSection"
            >
              <Row className="g-3">
                <Col md="6">
                  <ProductValidatedField
                    label={translate('fintrackApp.category.categoryType')}
                    id="category-categoryType"
                    name="categoryType"
                    data-cy="categoryType"
                    type="select"
                    disabled={isCategoryTypeReadOnly}
                    onChange={event => setSelectedCategoryType(event.target.value as keyof typeof CategoryType)}
                  >
                    {categoryTypeValues.map(categoryType => (
                      <option value={categoryType} key={categoryType}>
                        {translate(`fintrackApp.CategoryType.${categoryType}`)}
                      </option>
                    ))}
                  </ProductValidatedField>
                </Col>
                <Col md="6">
                  {isNew ? (
                    <ProductValidatedField
                      id="category-parentCategory"
                      name="parentCategory"
                      data-cy="parentCategory"
                      label={translate('fintrackApp.category.parentCategory')}
                      type="select"
                    >
                      <option value="" key="0" />
                      {parentCategoryOptions.map(otherEntity => (
                        <option value={otherEntity.id} key={otherEntity.id}>
                          {getCategoryPathLabel(otherEntity, categories)}
                        </option>
                      ))}
                    </ProductValidatedField>
                  ) : (
                    <div>
                      <label className="form-label" htmlFor="category-parentCategory">
                        <Translate contentKey="fintrackApp.category.parentCategory">Parent category</Translate>
                      </label>
                      <input
                        id="category-parentCategory"
                        data-cy="parentCategory"
                        className="form-control"
                        value={
                          categoryEntity?.parentCategory
                            ? getCategoryPathLabel(categoryEntity.parentCategory, categories)
                            : translate('fintrackApp.category.noParent')
                        }
                        disabled
                        readOnly
                      />
                      <FormText data-cy="categoryParentImmutableHelp">
                        <Translate contentKey="fintrackApp.category.parentImmutableHelp">
                          Parent category cannot be changed after creation.
                        </Translate>
                      </FormText>
                    </div>
                  )}
                </Col>
              </Row>
              <FormText className="d-block mt-2">
                <Translate contentKey={`fintrackApp.category.typeHelp.${selectedCategoryType}`}>
                  Use this category for outgoing transactions.
                </Translate>
                {!isNew && !isCategoryTypeReadOnly ? (
                  <span>
                    {' '}
                    <Translate contentKey="fintrackApp.category.typeUsageWarning">
                      Changing the type may be unavailable when this category is already in use.
                    </Translate>
                  </span>
                ) : null}
              </FormText>
            </ProductSection>
            <ProductSection title={<Translate contentKey="fintrackApp.category.color">Color</Translate>} dataCy="categoryColorSection">
              <HexColorControl
                name="color"
                pickerId="category-colorPicker"
                colorInputId="category-color"
                colorValue={colorValue}
                onChange={setColorValue}
                pickerLabel={translate('fintrackApp.category.colorPicker')}
                colorLabel={translate('fintrackApp.category.color')}
                dataCyPrefix="category"
              />
            </ProductSection>
          </div>
          <div className="d-flex justify-content-end gap-2 mt-4">
            <Button tag={Link} to="/category" replace color="secondary" outline>
              <Translate contentKey="entity.action.cancel">Cancel</Translate>
            </Button>
            <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
              <FontAwesomeIcon icon="save" /> <Translate contentKey="entity.action.save">Save</Translate>
            </Button>
          </div>
        </ProductValidatedForm>
      )}
    </ProductPage>
  );
};

export default CategoryUpdate;
