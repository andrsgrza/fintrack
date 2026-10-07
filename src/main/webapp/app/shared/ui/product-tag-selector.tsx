import React, { useState } from 'react';
import { Button, Input } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';

export type ProductTagSelectorTag = {
  id?: number;
  name?: string | null;
  color?: string | null;
  active?: boolean | null;
};

const tagIdentityStyle = (color?: string | null): React.CSSProperties | undefined =>
  color
    ? {
        backgroundColor: `${color}14`,
        borderColor: `${color}80`,
        borderInlineStart: `0.3rem solid ${color}`,
      }
    : undefined;

const tagKey = (tag: ProductTagSelectorTag, index: number) => tag.id?.toString() ?? `${tag.name ?? 'tag'}-${index}`;

const sameTag = (left: ProductTagSelectorTag, right: ProductTagSelectorTag) =>
  left.id !== undefined && right.id !== undefined ? left.id === right.id : left.name === right.name;

/**
 * Product-level multi-tag selector. Callers provide the currently selected values and selectable catalog;
 * this component deliberately has no workflow, API, or persistence knowledge.
 */
export const ProductTagSelector = ({
  selectedTags,
  availableTags,
  onChange,
  disabled = false,
  loading = false,
  error = false,
  id = 'product-tag-selector',
  dataCy = 'productTagSelector',
}: {
  selectedTags: ProductTagSelectorTag[];
  availableTags: ProductTagSelectorTag[];
  onChange: (tags: ProductTagSelectorTag[]) => void;
  disabled?: boolean;
  loading?: boolean;
  error?: boolean;
  id?: string;
  dataCy?: string;
}) => {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [search, setSearch] = useState('');
  const normalizedSearch = search.trim().toLocaleLowerCase();
  // The selectable catalog is the freshest source for presentation metadata such as active/color.
  // Keep a selected historical value if the catalog is unavailable or intentionally omits it.
  const displayedSelectedTags = selectedTags.map(tag => availableTags.find(availableTag => sameTag(availableTag, tag)) ?? tag);
  const availableOptions = availableTags.filter(
    tag =>
      tag.active !== false &&
      !selectedTags.some(selectedTag => sameTag(selectedTag, tag)) &&
      (!normalizedSearch || tag.name?.toLocaleLowerCase().includes(normalizedSearch)),
  );

  const removeTag = (tag: ProductTagSelectorTag) => {
    onChange(selectedTags.filter(selectedTag => !sameTag(selectedTag, tag)));
  };

  const addTag = (tag: ProductTagSelectorTag) => {
    if (selectedTags.some(selectedTag => sameTag(selectedTag, tag))) {
      return;
    }
    onChange([...selectedTags, tag]);
    setSearch('');
    setPickerOpen(false);
  };

  return (
    <div data-cy={dataCy} data-testid={dataCy} aria-label={translate('fintrackApp.tag.selector.label')}>
      <div className="d-flex flex-wrap align-items-center gap-2" data-cy={`${dataCy}Selected`} data-testid={`${dataCy}Selected`}>
        {displayedSelectedTags.length ? (
          displayedSelectedTags.map((tag, index) => (
            <span
              key={tagKey(tag, index)}
              className="d-inline-flex align-items-center rounded-pill border py-1 ps-2 pe-1 small fw-semibold"
              data-cy={`${dataCy}Chip`}
              data-testid={`${dataCy}Chip`}
              data-color-treatment="tag"
              style={tagIdentityStyle(tag.color)}
            >
              <span>{tag.name}</span>
              {tag.active === false ? (
                <span className="ms-1 text-muted" data-cy={`${dataCy}Inactive`} data-testid={`${dataCy}Inactive`}>
                  · <Translate contentKey="fintrackApp.tag.selector.inactive">Inactive</Translate>
                </span>
              ) : null}
              <button
                className="btn btn-sm border-0 p-0 ms-1 lh-1 text-body"
                type="button"
                aria-label={translate('fintrackApp.tag.selector.remove', { name: tag.name ?? '' })}
                title={translate('fintrackApp.tag.selector.remove', { name: tag.name ?? '' })}
                onClick={() => removeTag(tag)}
                disabled={disabled}
                data-cy={`${dataCy}Remove`}
                data-testid={`${dataCy}Remove`}
              >
                ×
              </button>
            </span>
          ))
        ) : (
          <span className="text-muted small" data-cy={`${dataCy}Empty`}>
            <Translate contentKey="fintrackApp.tag.selector.empty">No tags</Translate>
          </span>
        )}

        <Button
          color="secondary"
          outline
          size="sm"
          type="button"
          disabled={disabled || loading}
          onClick={() => setPickerOpen(open => !open)}
          aria-expanded={pickerOpen}
          data-cy={`${dataCy}Add`}
          data-testid={`${dataCy}Add`}
        >
          {loading ? (
            <Translate contentKey="fintrackApp.tag.selector.loading">Loading tags…</Translate>
          ) : (
            <>
              + <Translate contentKey="fintrackApp.tag.selector.add">Add tag</Translate>
            </>
          )}
        </Button>
      </div>

      {error ? (
        <div className="small text-danger mt-2" role="alert" data-cy={`${dataCy}Error`}>
          <Translate contentKey="fintrackApp.tag.selector.loadError">Tags could not be loaded. Existing tags are unchanged.</Translate>
        </div>
      ) : null}

      {pickerOpen && !disabled ? (
        <div className="border rounded-2 bg-white p-2 mt-2" data-cy={`${dataCy}Picker`} data-testid={`${dataCy}Picker`}>
          <label className="visually-hidden" htmlFor={`${id}-search`}>
            <Translate contentKey="fintrackApp.tag.selector.search">Search tags</Translate>
          </label>
          <Input
            id={`${id}-search`}
            type="search"
            value={search}
            onChange={event => setSearch(event.target.value)}
            placeholder={translate('fintrackApp.tag.selector.search')}
            disabled={loading}
            data-cy={`${dataCy}Search`}
            data-testid={`${dataCy}Search`}
          />
          <div
            className="d-flex flex-column align-items-stretch gap-1 mt-2"
            role="listbox"
            aria-label={translate('fintrackApp.tag.selector.search')}
          >
            {availableOptions.length ? (
              availableOptions.map((tag, index) => (
                <Button
                  key={tagKey(tag, index)}
                  color="light"
                  className="border text-start"
                  type="button"
                  onClick={() => addTag(tag)}
                  data-cy={`${dataCy}Option`}
                  data-testid={`${dataCy}Option`}
                  style={tagIdentityStyle(tag.color)}
                >
                  {tag.name}
                </Button>
              ))
            ) : (
              <span className="text-muted small px-1 py-2" data-cy={`${dataCy}NoOptions`}>
                <Translate contentKey="fintrackApp.tag.selector.noMatches">No tags found</Translate>
              </span>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
};
