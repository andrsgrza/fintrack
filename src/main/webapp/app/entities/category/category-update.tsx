import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getCategories } from 'app/entities/category/category.reducer';
import { CategoryType } from 'app/shared/model/enumerations/category-type.model';
import { createEntity, getEntity, partialUpdateEntity, reset } from './category.reducer';

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
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (isNew) {
      const now = displayDefaultDateTime();
      const entity = {
        name: values.name,
        categoryType: values.categoryType,
        color: values.color,
        icon: values.icon,
        active: values.active ?? true,
        createdAt: convertDateTimeToServer(now),
        updatedAt: convertDateTimeToServer(now),
        parentCategory: values.parentCategory ? categories.find(it => it.id.toString() === values.parentCategory?.toString()) : null,
      };
      dispatch(createEntity(entity));
    } else {
      const entity = {
        id: categoryEntity.id,
        name: values.name,
        ...(!isCategoryTypeReadOnly ? { categoryType: values.categoryType } : {}),
        color: values.color,
        icon: values.icon,
        active: values.active,
      };
      dispatch(partialUpdateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          categoryType: 'EXPENSE',
          active: true,
        }
      : {
          ...categoryEntity,
        };

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
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
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
              <ValidatedField
                label={translate('fintrackApp.category.color')}
                id="category-color"
                name="color"
                data-cy="color"
                type="text"
                validate={{
                  pattern: {
                    value: /^#[0-9A-Fa-f]{6}$/,
                    message: translate('entity.validation.pattern', { pattern: '^#[0-9A-Fa-f]{6}$' }),
                  },
                }}
              />
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
                      {otherEntity.name}
                    </option>
                  ))}
                </ValidatedField>
              ) : (
                <ValidatedField
                  label={translate('fintrackApp.category.parentCategory')}
                  id="category-parentCategory"
                  data-cy="parentCategory"
                  name="parentCategoryName"
                  type="text"
                  value={categoryEntity?.parentCategory?.name ?? ''}
                  disabled
                />
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
