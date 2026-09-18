import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { CategoryType } from 'app/shared/model/enumerations/category-type.model';
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
  const [selectedColor, setSelectedColor] = useState<string | null>(null);
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
    setSelectedColor(categoryEntity?.color && /^#[0-9A-Fa-f]{6}$/.test(categoryEntity.color) ? categoryEntity.color : null);
  }, [categoryEntity?.color]);

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
        color: values.color,
        icon: values.icon,
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
        color: values.color,
        icon: values.icon,
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
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.category.home.createOrEditLabel" data-cy="CategoryCreateUpdateHeading">
            <Translate contentKey="fintrackApp.category.home.createOrEditLabel">Create or edit a Category</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues} onSubmit={saveEntity} key={categoryEntity.id ?? 'new'}>
              {saveErrorKey ? (
                <Alert color="danger" fade={false} data-cy="categorySaveError" data-testid="categorySaveError">
                  <Translate contentKey={saveErrorKey}>The category could not be saved. Review its values and try again.</Translate>
                </Alert>
              ) : null}
              <ValidatedField
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
              <ValidatedField
                label={translate('fintrackApp.category.description')}
                id="category-description"
                name="description"
                data-cy="description"
                type="textarea"
                validate={{
                  maxLength: { value: 300, message: translate('entity.validation.maxlength', { max: 300 }) },
                }}
              />
              <ValidatedField
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
              </ValidatedField>
              <FormText className="mb-3 d-block">
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
              <ValidatedField
                label={translate('fintrackApp.category.color')}
                id="category-color"
                name="color"
                data-cy="color"
                type="text"
                onChange={event => setSelectedColor(/^#[0-9A-Fa-f]{6}$/.test(event.target.value) ? event.target.value : null)}
                validate={{
                  pattern: {
                    value: /^#[0-9A-Fa-f]{6}$/,
                    message: translate('entity.validation.pattern', { pattern: '^#[0-9A-Fa-f]{6}$' }),
                  },
                }}
              />
              <FormText className="mb-3 d-flex align-items-center gap-2" data-cy="categoryColorPreview">
                <Translate contentKey="fintrackApp.category.colorPreview">Preview</Translate>
                {selectedColor ? (
                  <span
                    aria-label={translate('fintrackApp.category.colorSwatch', { color: selectedColor })}
                    style={{
                      backgroundColor: selectedColor,
                      border: '1px solid #6c757d',
                      display: 'inline-block',
                      height: '1rem',
                      width: '1rem',
                    }}
                  />
                ) : (
                  <Translate contentKey="fintrackApp.category.noColor">No color selected</Translate>
                )}
              </FormText>
              <ValidatedField
                label={translate('fintrackApp.category.icon')}
                id="category-icon"
                name="icon"
                data-cy="icon"
                type="text"
                validate={{
                  maxLength: { value: 50, message: translate('entity.validation.maxlength', { max: 50 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.category.active')}
                id="category-active"
                name="active"
                data-cy="active"
                check
                type="checkbox"
              />
              {!isNew ? (
                <FormText className="mb-3 d-block">
                  <Translate contentKey="fintrackApp.category.inactiveFormHelp">
                    Inactive categories remain in historical records and cannot be newly assigned until reactivated.
                  </Translate>
                </FormText>
              ) : null}
              {isNew ? (
                <ValidatedField
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
                </ValidatedField>
              ) : (
                <div className="mb-3">
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/category" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default CategoryUpdate;
