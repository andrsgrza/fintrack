import React, { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTag from 'app/../i18n/en/tag.json';
import { ProductTagSelector, ProductTagSelectorTag } from './product-tag-selector';

const personal = { id: 1, name: 'Personal', color: '#E31B23', active: true };
const work = { id: 2, name: 'Work', color: '#2463A5', active: true };
const historical = { id: 3, name: 'Former project', color: '#663399', active: false };

const SelectorHarness = ({
  initialTags = [personal, historical],
  selectableTags = [personal, work, historical],
}: {
  initialTags?: ProductTagSelectorTag[];
  selectableTags?: ProductTagSelectorTag[];
}) => {
  const [selectedTags, setSelectedTags] = useState(initialTags);
  return <ProductTagSelector selectedTags={selectedTags} availableTags={selectableTags} onChange={setSelectedTags} />;
};

describe('ProductTagSelector', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('en', enFinancialTransaction);
    TranslatorContext.registerTranslations('en', enTag);
    TranslatorContext.setLocale('en');
  });

  it('renders selected color chips, keeps inactive history visible, and removes only the requested tag', () => {
    render(<SelectorHarness />);

    expect(screen.getAllByTestId('productTagSelectorChip')).toHaveLength(2);
    expect(screen.getByText('Personal').parentElement?.getAttribute('style')).toContain('border-inline-start');
    expect(screen.getByText('Former project')).toBeTruthy();
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(document.querySelector('select[multiple]')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Remove Personal' }));
    expect(screen.queryByText('Personal')).toBeNull();
    expect(screen.getByText('Former project')).toBeTruthy();
  });

  it('opens a searchable active-only picker and cannot add a selected tag twice', () => {
    render(<SelectorHarness initialTags={[personal, historical]} />);

    fireEvent.click(screen.getByRole('button', { name: /add tag/i }));
    fireEvent.change(screen.getByTestId('productTagSelectorSearch'), { target: { value: 'wor' } });
    expect(screen.getByRole('button', { name: 'Work' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Personal' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Former project' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Work' }));
    expect(screen.getByText('Work')).toBeTruthy();
    expect(screen.getAllByText('Work')).toHaveLength(1);
  });

  it('uses refreshed selectable metadata to label a selected tag that became inactive', () => {
    render(<SelectorHarness initialTags={[personal]} selectableTags={[{ ...personal, active: false }]} />);

    expect(screen.getByTestId('productTagSelectorInactive')).toBeTruthy();
  });
});
