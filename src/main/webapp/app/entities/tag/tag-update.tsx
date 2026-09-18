import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { HexColorControl, isHexColor } from 'app/shared/ui/hex-color-control';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { CompactProductStatusControl } from 'app/shared/ui/product-status-control';
import { ProductValidatedField, ProductValidatedForm } from 'app/shared/ui/product-validated-form';

import { createEntity, getEntity, partialUpdateEntity, reset } from './tag.reducer';

const tagSaveErrorKey = (result: unknown) => {
  const error = (result as { error?: { message?: string; response?: { data?: { detail?: string } } } }).error;
  const detail = `${error?.response?.data?.detail ?? ''} ${error?.message ?? ''}`.toLowerCase();
  if (detail.includes('already exists')) return 'fintrackApp.tag.errors.duplicate';
  if (detail.includes('color')) return 'fintrackApp.tag.errors.invalidColor';
  if (detail.includes('inactive')) return 'fintrackApp.tag.errors.inactiveReference';
  return 'fintrackApp.tag.errors.saveFailed';
};

export const TagUpdate = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const tagEntity = useAppSelector(state => state.tag.entity);
  const loading = useAppSelector(state => state.tag.loading);
  const updating = useAppSelector(state => state.tag.updating);
  const updateSuccess = useAppSelector(state => state.tag.updateSuccess);
  const [colorValue, setColorValue] = useState(isHexColor(tagEntity.color) ? tagEntity.color! : '');
  const [active, setActive] = useState(true);
  const [saveErrorKey, setSaveErrorKey] = useState<string | null>(null);

  const handleClose = () => {
    navigate('/tag');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }
  }, []);

  useEffect(() => {
    setColorValue(isHexColor(tagEntity.color) ? tagEntity.color! : '');
  }, [tagEntity?.color]);

  useEffect(() => {
    setActive(isNew ? true : tagEntity.active !== false);
  }, [isNew, tagEntity.active]);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    setSaveErrorKey(null);
    const entity = {
      name: values.name,
      description: values.description || null,
      color: colorValue || null,
      active: values.active ?? true,
    };

    const action = isNew ? createEntity(entity) : partialUpdateEntity({ id: tagEntity.id, ...entity });
    dispatch(action).then(result => {
      if (result.type.endsWith('/rejected')) {
        setSaveErrorKey(tagSaveErrorKey(result));
      }
    });
  };

  const defaultValues = useMemo(
    () =>
      isNew
        ? {
            active: true,
          }
        : {
            ...tagEntity,
          },
    [tagEntity, isNew],
  );

  return (
    <ProductPage>
      {loading ? (
        <p>Loading...</p>
      ) : (
        <ProductValidatedForm defaultValues={defaultValues} onSubmit={saveEntity} formKey={tagEntity.id ?? 'new'}>
          <ProductPageHeader
            headingId="fintrackApp.tag.home.createOrEditLabel"
            dataCy="TagCreateUpdateHeading"
            title={<Translate contentKey="fintrackApp.tag.home.createOrEditLabel">Create or edit tag</Translate>}
            metadata={
              <CompactProductStatusControl
                id="tag-active"
                name="active"
                active={active}
                onActiveChange={setActive}
                label={translate('fintrackApp.tag.status.label')}
                activeLabel={translate('fintrackApp.tag.status.active')}
                inactiveLabel={translate('fintrackApp.tag.status.inactive')}
                help={
                  <Translate contentKey="fintrackApp.tag.status.help">
                    Inactive tags are kept for history and cannot be newly assigned until reactivated.
                  </Translate>
                }
                dataCyPrefix="tag"
              />
            }
            actions={
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/tag" replace color="secondary" outline size="sm">
                <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
              </Button>
            }
          />
          {saveErrorKey ? (
            <Alert color="danger" fade={false} data-cy="tagSaveError" data-testid="tagSaveError">
              <Translate contentKey={saveErrorKey}>The tag could not be saved. Review its values and try again.</Translate>
            </Alert>
          ) : null}
          <div className="vstack gap-3">
            <ProductSection
              title={<Translate contentKey="fintrackApp.tag.basicInformation">Basic information</Translate>}
              dataCy="tagBasicInformationSection"
            >
              <ProductValidatedField
                label={translate('fintrackApp.tag.name')}
                id="tag-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 50, message: translate('entity.validation.maxlength', { max: 50 }) },
                }}
              />
              <ProductValidatedField
                label={translate('fintrackApp.tag.description')}
                id="tag-description"
                name="description"
                data-cy="description"
                type="textarea"
                validate={{
                  maxLength: { value: 250, message: translate('entity.validation.maxlength', { max: 250 }) },
                }}
              />
            </ProductSection>
            <ProductSection title={<Translate contentKey="fintrackApp.tag.color">Color</Translate>} dataCy="tagColorSection">
              <HexColorControl
                name="color"
                pickerId="tag-colorPicker"
                colorInputId="tag-color"
                colorValue={colorValue}
                onChange={setColorValue}
                pickerLabel={translate('fintrackApp.tag.colorPicker')}
                colorLabel={translate('fintrackApp.tag.color')}
                dataCyPrefix="tag"
              />
            </ProductSection>
          </div>
          <div className="d-flex justify-content-end gap-2 mt-4">
            <Button tag={Link} to="/tag" replace color="secondary" outline>
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

export default TagUpdate;
