import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Label } from 'reactstrap';
import { useNavigate } from 'react-router-dom';

import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { formatFinancialAccountLabel } from 'app/entities/financial-account/financial-account-labels';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { useAppSelector } from 'app/config/store';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ProductTagSelector } from 'app/shared/ui/product-tag-selector';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { BudgetType, CalendarPreviewDraft, createBudget, previewBudgetCalendar } from './budget-api';
import {
  browserTimeZone,
  browserToday,
  calendarValidationError,
  copy,
  dateRange,
  formatBudgetAmount,
  userFacingError,
} from './budget-utils';

export type ScopeMode = 'ALL' | 'SELECTED';
export type TagScopeMode = 'NONE' | 'SELECTED';
type IntervalUnit = '' | 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';
type Alignment = 'ANCHORED' | 'NATURAL' | 'CUSTOM';
type RecurrenceMode = 'ONCE' | 'RECURRING';
type Termination = 'NONE' | 'AFTER_N_PERIODS' | 'END_DATE';

const newRequestId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return `budget-${crypto.randomUUID()}`;
  return `budget-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
};

export const categoryCompatible = (category: ICategorySelectable, type: BudgetType) =>
  category.categoryType === 'BOTH' || category.categoryType === type;

const categoryLabel = (category: ICategorySelectable) =>
  category.parentCategoryName ? `${category.parentCategoryName} › ${category.name ?? ''}` : (category.name ?? '');

const toggleId = (values: number[], id?: number) => {
  if (id === undefined) return values;
  return values.includes(id) ? values.filter(value => value !== id) : [...values, id];
};

const currencyCodes = ['MXN', 'USD', 'EUR'];

const weekdays: Array<[number, string, string]> = [
  [1, 'Lunes', 'Monday'],
  [2, 'Martes', 'Tuesday'],
  [3, 'Miércoles', 'Wednesday'],
  [4, 'Jueves', 'Thursday'],
  [5, 'Viernes', 'Friday'],
  [6, 'Sábado', 'Saturday'],
  [7, 'Domingo', 'Sunday'],
];

const pad = (value: number) => String(value).padStart(2, '0');
const monthValue = (date: string) => date.slice(0, 7);
const yearValue = (date: string) => date.slice(0, 4);
const quarterValue = (date: string) => `${yearValue(date)}-Q${Math.floor((Number(date.slice(5, 7)) - 1) / 3) + 1}`;

/** Control-only conversion. Java BudgetPeriodCalculator remains authoritative for every rendered period. */
const isoWeekStart = (value: string) => {
  const matched = /^(\d{4})-W(\d{2})$/.exec(value);
  if (!matched) return '';
  const year = Number(matched[1]);
  const week = Number(matched[2]);
  const januaryFourth = new Date(Date.UTC(year, 0, 4));
  const weekday = januaryFourth.getUTCDay() || 7;
  januaryFourth.setUTCDate(januaryFourth.getUTCDate() - weekday + 1 + (week - 1) * 7);
  return januaryFourth.toISOString().slice(0, 10);
};

const isoWeekValue = (dateValue: string) => {
  const date = new Date(`${dateValue}T00:00:00Z`);
  const weekday = date.getUTCDay() || 7;
  date.setUTCDate(date.getUTCDate() + 4 - weekday);
  const firstThursday = new Date(Date.UTC(date.getUTCFullYear(), 0, 4));
  const firstWeekday = firstThursday.getUTCDay() || 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() + 4 - firstWeekday);
  const week = Math.round((date.getTime() - firstThursday.getTime()) / 604800000) + 1;
  return `${date.getUTCFullYear()}-W${pad(week)}`;
};

const dateInMonth = (month: string, requestedDay: number) => {
  const matched = /^(\d{4})-(\d{2})$/.exec(month);
  if (!matched || requestedDay < 1 || requestedDay > 31) return '';
  const year = Number(matched[1]);
  const monthNumber = Number(matched[2]);
  const lastDay = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate();
  return `${year}-${pad(monthNumber)}-${pad(Math.min(requestedDay, lastDay))}`;
};

const dateInYear = (year: string, month: number, requestedDay: number) => dateInMonth(`${year}-${pad(month)}`, requestedDay);

export const BudgetScopeSection = ({
  title,
  controlPrefix = '',
  locale,
  submitting,
  referencesLoading,
  currency,
  accountScope,
  setAccountScope,
  compatibleAccounts,
  accountIds,
  setAccountIds,
  categoryScope,
  setCategoryScope,
  compatibleCategories,
  categoryIds,
  setCategoryIds,
  tagScope,
  setTagScope,
  tagMatchMode,
  setTagMatchMode,
  selectedTags,
  tags,
  setTagIds,
  referenceError,
}: {
  title?: string;
  controlPrefix?: string;
  locale: string;
  submitting: boolean;
  referencesLoading: boolean;
  currency: string;
  accountScope: ScopeMode;
  setAccountScope: (value: ScopeMode) => void;
  compatibleAccounts: IFinancialAccountSelectable[];
  accountIds: number[];
  setAccountIds: React.Dispatch<React.SetStateAction<number[]>>;
  categoryScope: ScopeMode;
  setCategoryScope: (value: ScopeMode) => void;
  compatibleCategories: ICategorySelectable[];
  categoryIds: number[];
  setCategoryIds: React.Dispatch<React.SetStateAction<number[]>>;
  tagScope: TagScopeMode;
  setTagScope: (value: TagScopeMode) => void;
  tagMatchMode: 'ANY' | 'ALL';
  setTagMatchMode: (value: 'ANY' | 'ALL') => void;
  selectedTags: ITagSelectable[];
  tags: ITagSelectable[];
  setTagIds: React.Dispatch<React.SetStateAction<number[]>>;
  referenceError: string | null;
}) => (
  <ProductSection title={title ?? copy(locale, '3. Alcance', '3. Scope')}>
    <div className="d-flex flex-column gap-4">
      <fieldset disabled={submitting || referencesLoading}>
        <legend className="h6">{copy(locale, 'Cuentas', 'Accounts')}</legend>
        <div className="d-flex flex-wrap gap-3 mb-2">
          <Label check>
            <Input
              type="radio"
              name={controlPrefix ? `${controlPrefix}-accountScope` : 'accountScope'}
              checked={accountScope === 'ALL'}
              onChange={() => setAccountScope('ALL')}
            />{' '}
            {copy(locale, 'Todas las cuentas de esta moneda', 'All accounts in this currency')}
          </Label>
          <Label check>
            <Input
              type="radio"
              name={controlPrefix ? `${controlPrefix}-accountScope` : 'accountScope'}
              checked={accountScope === 'SELECTED'}
              onChange={() => setAccountScope('SELECTED')}
            />{' '}
            {copy(locale, 'Cuentas seleccionadas', 'Selected accounts')}
          </Label>
        </div>
        {accountScope === 'SELECTED' ? (
          <div className="border rounded-2 p-2" style={{ maxHeight: '14rem', overflowY: 'auto' }}>
            {!currency ? (
              <span className="small text-muted">{copy(locale, 'Primero selecciona una moneda.', 'Select a currency first.')}</span>
            ) : null}
            {currency && !compatibleAccounts.length ? (
              <span className="small text-muted">
                {copy(locale, 'No hay cuentas activas en esta moneda.', 'There are no active accounts in this currency.')}
              </span>
            ) : null}
            {currency
              ? compatibleAccounts.map(account => (
                  <Label check className="d-block border rounded-2 px-2 py-1 mb-1" key={account.id}>
                    <Input
                      type="checkbox"
                      checked={account.id !== undefined && accountIds.includes(account.id)}
                      onChange={() => setAccountIds(current => toggleId(current, account.id))}
                    />{' '}
                    {formatFinancialAccountLabel(account)}
                  </Label>
                ))
              : null}
          </div>
        ) : null}
      </fieldset>

      <fieldset disabled={submitting || referencesLoading}>
        <legend className="h6">{copy(locale, 'Categorías', 'Categories')}</legend>
        <div className="d-flex flex-wrap gap-3 mb-2">
          <Label check>
            <Input
              type="radio"
              name={controlPrefix ? `${controlPrefix}-categoryScope` : 'categoryScope'}
              checked={categoryScope === 'ALL'}
              onChange={() => setCategoryScope('ALL')}
            />{' '}
            {copy(locale, 'Todas las categorías compatibles', 'All compatible categories')}
          </Label>
          <Label check>
            <Input
              type="radio"
              name={controlPrefix ? `${controlPrefix}-categoryScope` : 'categoryScope'}
              checked={categoryScope === 'SELECTED'}
              onChange={() => setCategoryScope('SELECTED')}
            />{' '}
            {copy(locale, 'Categorías seleccionadas y sus subcategorías', 'Selected categories and their subcategories')}
          </Label>
        </div>
        {categoryScope === 'SELECTED' ? (
          <div className="border rounded-2 p-2" style={{ maxHeight: '14rem', overflowY: 'auto' }}>
            {!compatibleCategories.length ? (
              <span className="small text-muted">
                {copy(locale, 'No hay categorías compatibles.', 'There are no compatible categories.')}
              </span>
            ) : null}
            {compatibleCategories.map(category => (
              <Label
                check
                className="d-block border rounded-2 px-2 py-1 mb-1"
                key={category.id}
                style={category.color ? { borderInlineStart: `0.3rem solid ${category.color}` } : undefined}
              >
                <Input
                  type="checkbox"
                  checked={category.id !== undefined && categoryIds.includes(category.id)}
                  onChange={() => setCategoryIds(current => toggleId(current, category.id))}
                />{' '}
                {categoryLabel(category)}
              </Label>
            ))}
          </div>
        ) : null}
      </fieldset>

      <fieldset disabled={submitting || referencesLoading}>
        <legend className="h6">{copy(locale, 'Etiquetas', 'Tags')}</legend>
        <div className="d-flex flex-wrap gap-3 mb-2">
          <Label check>
            <Input
              type="radio"
              name={controlPrefix ? `${controlPrefix}-tagScope` : 'tagScope'}
              checked={tagScope === 'NONE'}
              onChange={() => setTagScope('NONE')}
            />{' '}
            {copy(locale, 'Sin filtrar por etiquetas', 'Do not filter by tags')}
          </Label>
          <Label check>
            <Input
              type="radio"
              name={controlPrefix ? `${controlPrefix}-tagScope` : 'tagScope'}
              checked={tagScope === 'SELECTED'}
              onChange={() => setTagScope('SELECTED')}
            />{' '}
            {copy(locale, 'Etiquetas seleccionadas', 'Selected tags')}
          </Label>
        </div>
        {tagScope === 'SELECTED' ? (
          <>
            <div className="d-flex flex-wrap gap-3 mb-2">
              <Label check>
                <Input
                  type="radio"
                  name={controlPrefix ? `${controlPrefix}-tagMatchMode` : 'tagMatchMode'}
                  checked={tagMatchMode === 'ANY'}
                  onChange={() => setTagMatchMode('ANY')}
                />{' '}
                {copy(locale, 'Cualquiera de las etiquetas', 'Any selected tag')}
              </Label>
              <Label check>
                <Input
                  type="radio"
                  name={controlPrefix ? `${controlPrefix}-tagMatchMode` : 'tagMatchMode'}
                  checked={tagMatchMode === 'ALL'}
                  onChange={() => setTagMatchMode('ALL')}
                />{' '}
                {copy(locale, 'Todas las etiquetas', 'All selected tags')}
              </Label>
            </div>
            <ProductTagSelector
              id={controlPrefix ? `${controlPrefix}-tags` : 'budget-tags'}
              dataCy={controlPrefix ? `${controlPrefix}TagSelector` : 'budgetTagSelector'}
              selectedTags={selectedTags}
              availableTags={tags}
              onChange={next => setTagIds(next.flatMap(tag => (tag.id === undefined ? [] : [tag.id])))}
              loading={referencesLoading}
              error={!!referenceError}
            />
          </>
        ) : null}
      </fieldset>
    </div>
  </ProductSection>
);

const BudgetCreate = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const navigate = useNavigate();
  const timeZone = useMemo(browserTimeZone, []);
  const today = useMemo(browserToday, []);
  const requestId = useRef(newRequestId());
  const previewRequest = useRef<AbortController | null>(null);
  const submissionInFlight = useRef(false);

  const [type, setType] = useState<BudgetType>('EXPENSE');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('');
  const [intervalCount, setIntervalCount] = useState('');
  const [intervalUnit, setIntervalUnit] = useState<IntervalUnit>('');
  const [alignment, setAlignment] = useState<Alignment>('ANCHORED');
  const [anchorDate, setAnchorDate] = useState(today);
  const [naturalWeek, setNaturalWeek] = useState(() => isoWeekValue(today));
  const [naturalMonth, setNaturalMonth] = useState(() => monthValue(today));
  const [naturalQuarter, setNaturalQuarter] = useState(() => quarterValue(today));
  const [naturalYear, setNaturalYear] = useState(() => yearValue(today));
  const [customWeek, setCustomWeek] = useState(() => isoWeekValue(today));
  const [customWeekday, setCustomWeekday] = useState('1');
  const [customMonth, setCustomMonth] = useState(() => monthValue(today));
  const [customQuarter, setCustomQuarter] = useState(() => monthValue(today));
  const [customYear, setCustomYear] = useState(() => yearValue(today));
  const [customYearMonth, setCustomYearMonth] = useState(() => String(Number(today.slice(5, 7))));
  const [customDayOfMonth, setCustomDayOfMonth] = useState(() => String(Number(today.slice(8, 10))));
  const [recurrenceMode, setRecurrenceMode] = useState<RecurrenceMode>('RECURRING');
  const [termination, setTermination] = useState<Termination>('NONE');
  const [terminationCount, setTerminationCount] = useState('');
  const [terminationDate, setTerminationDate] = useState('');
  const [accountScope, setAccountScope] = useState<ScopeMode>('ALL');
  const [accountIds, setAccountIds] = useState<number[]>([]);
  const [categoryScope, setCategoryScope] = useState<ScopeMode>('ALL');
  const [categoryIds, setCategoryIds] = useState<number[]>([]);
  const [tagScope, setTagScope] = useState<TagScopeMode>('NONE');
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [tagMatchMode, setTagMatchMode] = useState<'ANY' | 'ALL'>('ANY');
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [categories, setCategories] = useState<ICategorySelectable[]>([]);
  const [tags, setTags] = useState<ITagSelectable[]>([]);
  const [referencesLoading, setReferencesLoading] = useState(true);
  const [referenceError, setReferenceError] = useState<string | null>(null);
  const [preview, setPreview] = useState<{
    localToday: string;
    periods: Array<{ index: number; startInclusive: string; endExclusive: string }>;
  } | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    void Promise.all([getSelectableFinancialAccounts(), getSelectableCategories(), getSelectableTags()])
      .then(([nextAccounts, nextCategories, nextTags]) => {
        if (!active) return;
        setAccounts(nextAccounts);
        setCategories(nextCategories);
        setTags(nextTags);
      })
      .catch(problem => {
        if (active)
          setReferenceError(
            userFacingError(
              problem,
              copy(locale, 'No se pudieron cargar las opciones del presupuesto.', 'Budget options could not be loaded.'),
            ),
          );
      })
      .finally(() => {
        if (active) setReferencesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [locale]);

  const effectiveTermination = recurrenceMode === 'ONCE' ? 'NONE' : termination;
  const numericInterval = Number(intervalCount);
  const numericCustomDay = Number(customDayOfMonth);
  const effectiveAnchorDate = useMemo(() => {
    if (alignment === 'ANCHORED' || intervalUnit === 'DAY') return anchorDate;
    if (alignment === 'NATURAL') {
      if (intervalUnit === 'WEEK') return isoWeekStart(naturalWeek);
      if (intervalUnit === 'MONTH') return naturalMonth ? `${naturalMonth}-01` : '';
      if (intervalUnit === 'QUARTER') {
        const matched = /^(\d{4})-Q([1-4])$/.exec(naturalQuarter);
        return matched ? `${matched[1]}-${pad((Number(matched[2]) - 1) * 3 + 1)}-01` : '';
      }
      if (intervalUnit === 'YEAR') return naturalYear ? `${naturalYear}-01-01` : '';
      return '';
    }
    if (intervalUnit === 'WEEK') {
      const monday = isoWeekStart(customWeek);
      if (!monday) return '';
      const date = new Date(`${monday}T00:00:00Z`);
      date.setUTCDate(date.getUTCDate() + Number(customWeekday) - 1);
      return date.toISOString().slice(0, 10);
    }
    if (intervalUnit === 'MONTH') return dateInMonth(customMonth, numericCustomDay);
    if (intervalUnit === 'QUARTER') return dateInMonth(customQuarter, numericCustomDay);
    if (intervalUnit === 'YEAR') return dateInYear(customYear, Number(customYearMonth), numericCustomDay);
    return '';
  }, [
    alignment,
    anchorDate,
    customDayOfMonth,
    customMonth,
    customQuarter,
    customWeek,
    customWeekday,
    customYear,
    customYearMonth,
    intervalUnit,
    naturalMonth,
    naturalQuarter,
    naturalWeek,
    naturalYear,
    numericCustomDay,
  ]);
  const effectiveCustomDay =
    alignment === 'CUSTOM' && ['MONTH', 'QUARTER', 'YEAR'].includes(intervalUnit) && numericCustomDay >= 1 && numericCustomDay <= 31
      ? numericCustomDay
      : null;
  const calendarReady =
    Number.isInteger(numericInterval) &&
    numericInterval > 0 &&
    intervalUnit !== '' &&
    effectiveAnchorDate !== '' &&
    (alignment !== 'CUSTOM' || intervalUnit === 'WEEK' || effectiveCustomDay !== null) &&
    (effectiveTermination !== 'AFTER_N_PERIODS' || (Number.isInteger(Number(terminationCount)) && Number(terminationCount) > 0)) &&
    (effectiveTermination !== 'END_DATE' || terminationDate !== '');

  useEffect(() => {
    previewRequest.current?.abort();
    if (!calendarReady) {
      setPreview(null);
      setPreviewError(null);
      setPreviewLoading(false);
      return;
    }
    const controller = new AbortController();
    previewRequest.current = controller;
    const timer = window.setTimeout(() => {
      setPreviewLoading(true);
      setPreviewError(null);
      const draft: CalendarPreviewDraft = {
        timeZone,
        intervalCount: numericInterval,
        intervalUnit,
        alignment,
        anchorDate: effectiveAnchorDate,
        customDayOfMonth: effectiveCustomDay,
        recurrenceMode,
        termination: effectiveTermination,
        terminationPeriodCount: effectiveTermination === 'AFTER_N_PERIODS' ? Number(terminationCount) : null,
        recurrenceEndDate: effectiveTermination === 'END_DATE' ? terminationDate : null,
      };
      void previewBudgetCalendar(draft, controller.signal)
        .then(next => {
          if (!controller.signal.aborted) setPreview(next);
        })
        .catch(problem => {
          if (!controller.signal.aborted) {
            setPreview(null);
            setPreviewError(
              calendarValidationError(
                problem,
                locale,
                copy(locale, 'No se pudo validar el calendario.', 'The calendar could not be validated.'),
              ),
            );
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setPreviewLoading(false);
        });
    }, 180);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [
    alignment,
    effectiveAnchorDate,
    effectiveCustomDay,
    calendarReady,
    effectiveTermination,
    intervalUnit,
    locale,
    numericInterval,
    recurrenceMode,
    terminationCount,
    terminationDate,
    timeZone,
  ]);

  const compatibleAccounts = useMemo(() => accounts.filter(account => !currency || account.currency === currency), [accounts, currency]);
  const compatibleCategories = useMemo(() => categories.filter(category => categoryCompatible(category, type)), [categories, type]);
  const selectedTags = useMemo(() => tags.filter(tag => tag.id !== undefined && tagIds.includes(tag.id)), [tagIds, tags]);

  const updateCurrency = (nextCurrency: string) => {
    setCurrency(nextCurrency);
    setAccountIds(current => current.filter(id => accounts.some(account => account.id === id && account.currency === nextCurrency)));
  };

  const updateType = (nextType: BudgetType) => {
    setType(nextType);
    setCategoryIds(current =>
      current.filter(id => categories.some(category => category.id === id && categoryCompatible(category, nextType))),
    );
  };

  const validate = () => {
    if (!name.trim()) return copy(locale, 'Escribe un nombre para el presupuesto.', 'Enter a budget name.');
    if (!Number.isFinite(Number(amount)) || Number(amount) <= 0)
      return copy(locale, 'Escribe un importe objetivo mayor que cero.', 'Enter a target amount greater than zero.');
    if (!currency) return copy(locale, 'Selecciona una moneda para continuar.', 'Select a currency to continue.');
    if (!calendarReady || previewError || !preview?.periods.length) {
      return copy(
        locale,
        'Completa un calendario válido antes de crear el presupuesto.',
        'Complete a valid calendar before creating the budget.',
      );
    }
    if (accountScope === 'SELECTED' && !accountIds.length)
      return copy(locale, 'Selecciona al menos una cuenta.', 'Select at least one account.');
    if (categoryScope === 'SELECTED' && !categoryIds.length)
      return copy(locale, 'Selecciona al menos una categoría.', 'Select at least one category.');
    if (tagScope === 'SELECTED' && !tagIds.length) return copy(locale, 'Selecciona al menos una etiqueta.', 'Select at least one tag.');
    return null;
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting || submissionInFlight.current) return;
    const invalid = validate();
    if (invalid) {
      setFormError(invalid);
      return;
    }
    setFormError(null);
    submissionInFlight.current = true;
    setSubmitting(true);
    try {
      const created = await createBudget({
        name: name.trim(),
        description: description.trim() || null,
        type,
        currency,
        clientRequestId: requestId.current,
        timeZone,
        initialRevision: {
          targetAmount: Number(amount),
          intervalCount: numericInterval,
          intervalUnit,
          alignment,
          anchorDate: effectiveAnchorDate,
          customDayOfMonth: effectiveCustomDay,
          recurrenceMode,
          termination: effectiveTermination,
          terminationPeriodCount: effectiveTermination === 'AFTER_N_PERIODS' ? Number(terminationCount) : null,
          recurrenceEndDate: effectiveTermination === 'END_DATE' ? terminationDate : null,
          accountScopeMode: accountScope,
          accountIds: accountScope === 'SELECTED' ? accountIds : [],
          categoryScopeMode: categoryScope,
          categoryIds: categoryScope === 'SELECTED' ? categoryIds : [],
          tagScopeMode: tagScope,
          tagIds: tagScope === 'SELECTED' ? tagIds : [],
          tagMatchMode,
        },
      });
      navigate(`/budgets/${created.id}?referenceDate=${effectiveAnchorDate}`);
    } catch (problem) {
      setFormError(
        calendarValidationError(
          problem,
          locale,
          copy(
            locale,
            'No se pudo crear el presupuesto. Tu información sigue aquí.',
            'The budget could not be created. Your draft is still here.',
          ),
        ),
      );
    } finally {
      submissionInFlight.current = false;
      setSubmitting(false);
    }
  };

  const recurrenceSummary = () => {
    if (!preview?.periods.length)
      return copy(locale, 'Completa el calendario para revisar los períodos.', 'Complete the calendar to review periods.');
    const unit = (
      {
        DAY: ['día', 'day'],
        WEEK: ['semana', 'week'],
        MONTH: ['mes', 'month'],
        QUARTER: ['trimestre', 'quarter'],
        YEAR: ['año', 'year'],
      } as Record<string, [string, string]>
    )[intervalUnit];
    const interval = `${numericInterval} ${copy(locale, unit[0], unit[1])}${numericInterval === 1 ? '' : copy(locale, 's', 's')}`;
    if (recurrenceMode === 'ONCE') return copy(locale, `Una sola vez, cada ${interval}.`, `One time, every ${interval}.`);
    if (effectiveTermination === 'NONE')
      return copy(locale, `Se repite cada ${interval} sin fecha final.`, `Repeats every ${interval} with no end date.`);
    if (effectiveTermination === 'AFTER_N_PERIODS')
      return copy(locale, `Se repite ${terminationCount} períodos completos.`, `Repeats for ${terminationCount} complete periods.`);
    return copy(locale, `Termina en el límite completo ${terminationDate}.`, `Ends at the complete boundary ${terminationDate}.`);
  };

  return (
    <ProductPage wide className="budget-create">
      <ProductPageHeader
        title={copy(locale, 'Crear presupuesto', 'Create budget')}
        subtitle={copy(
          locale,
          'Define un límite de gasto o una meta de ingresos. Las transferencias internas vinculadas no cuentan como gasto o ingreso económico.',
          'Set an expense limit or income goal. Linked internal transfers do not count as economic expense or income.',
        )}
        actions={
          <Button color="secondary" outline onClick={() => navigate('/budgets')} disabled={submitting}>
            {copy(locale, 'Volver a presupuestos', 'Back to budgets')}
          </Button>
        }
        dataCy="budgetCreateHeading"
      />

      {referenceError ? (
        <div className="alert alert-danger" role="alert">
          {referenceError}
        </div>
      ) : null}
      {formError ? (
        <div className="alert alert-danger" role="alert" data-cy="budgetCreateError">
          {formError}
        </div>
      ) : null}

      <form onSubmit={submit} noValidate>
        <div className="d-flex flex-column gap-4">
          <ProductSection title={copy(locale, '1. Objetivo', '1. Objective')}>
            <div className="row g-3">
              <div className="col-12 col-md-6">
                <Label for="budget-type">{copy(locale, 'Quiero controlar', 'I want to track')}</Label>
                <Input
                  id="budget-type"
                  type="select"
                  value={type}
                  onChange={event => updateType(event.target.value as BudgetType)}
                  disabled={submitting}
                >
                  <option value="EXPENSE">{copy(locale, 'Gastos económicos', 'Economic expenses')}</option>
                  <option value="INCOME">{copy(locale, 'Ingresos económicos', 'Economic income')}</option>
                </Input>
              </div>
              <div className="col-12 col-md-6">
                <Label for="budget-name">{copy(locale, 'Nombre', 'Name')}</Label>
                <Input
                  id="budget-name"
                  value={name}
                  maxLength={100}
                  onChange={event => setName(event.target.value)}
                  disabled={submitting}
                  required
                  data-cy="budgetName"
                />
              </div>
              <div className="col-12">
                <Label for="budget-description">{copy(locale, 'Descripción opcional', 'Optional description')}</Label>
                <Input
                  id="budget-description"
                  type="textarea"
                  rows="2"
                  value={description}
                  maxLength={500}
                  onChange={event => setDescription(event.target.value)}
                  disabled={submitting}
                />
              </div>
              <div className="col-12 col-md-6">
                <Label for="budget-amount">
                  {type === 'EXPENSE'
                    ? copy(locale, 'Límite del período', 'Period limit')
                    : copy(locale, 'Meta del período', 'Period goal')}
                </Label>
                <Input
                  id="budget-amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  inputMode="decimal"
                  value={amount}
                  onChange={event => setAmount(event.target.value)}
                  disabled={submitting}
                  required
                  data-cy="budgetTargetAmount"
                />
              </div>
              <div className="col-12 col-md-6">
                <Label for="budget-currency">{copy(locale, 'Moneda', 'Currency')}</Label>
                <Input
                  id="budget-currency"
                  type="select"
                  value={currency}
                  onChange={event => updateCurrency(event.target.value)}
                  disabled={submitting || referencesLoading}
                  required
                  data-cy="budgetCurrency"
                >
                  <option value="">{copy(locale, 'Selecciona una moneda…', 'Select a currency…')}</option>
                  {currencyCodes.map(code => (
                    <option key={code} value={code}>
                      {code}
                    </option>
                  ))}
                </Input>
              </div>
            </div>
          </ProductSection>

          <ProductSection title={copy(locale, '2. Calendario', '2. Calendar')}>
            <div className="row g-3">
              <div className="col-6 col-md-3">
                <Label for="budget-interval-count">{copy(locale, 'Cada', 'Every')}</Label>
                <Input
                  id="budget-interval-count"
                  type="number"
                  min="1"
                  step="1"
                  value={intervalCount}
                  onChange={event => setIntervalCount(event.target.value)}
                  disabled={submitting}
                  data-cy="budgetIntervalCount"
                />
              </div>
              <div className="col-6 col-md-3">
                <Label for="budget-interval-unit">{copy(locale, 'Unidad', 'Unit')}</Label>
                <Input
                  id="budget-interval-unit"
                  type="select"
                  value={intervalUnit}
                  onChange={event => {
                    const next = event.target.value as IntervalUnit;
                    setIntervalUnit(next);
                    if (next === 'DAY') setAlignment('ANCHORED');
                  }}
                  disabled={submitting}
                  data-cy="budgetIntervalUnit"
                >
                  <option value="">{copy(locale, 'Selecciona…', 'Select…')}</option>
                  <option value="DAY">{copy(locale, 'Días', 'Days')}</option>
                  <option value="WEEK">{copy(locale, 'Semanas', 'Weeks')}</option>
                  <option value="MONTH">{copy(locale, 'Meses', 'Months')}</option>
                  <option value="QUARTER">{copy(locale, 'Trimestres', 'Quarters')}</option>
                  <option value="YEAR">{copy(locale, 'Años', 'Years')}</option>
                </Input>
              </div>
              {intervalUnit !== 'DAY' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-alignment">{copy(locale, 'Modo de calendario', 'Calendar mode')}</Label>
                  <Input
                    id="budget-alignment"
                    type="select"
                    value={alignment}
                    onChange={event => setAlignment(event.target.value as Alignment)}
                    disabled={submitting}
                    data-cy="budgetCalendarMode"
                  >
                    <option value="ANCHORED">{copy(locale, 'Desde una fecha específica', 'From a specific date')}</option>
                    <option value="NATURAL">{copy(locale, 'Calendario natural', 'Natural calendar')}</option>
                    <option value="CUSTOM">{copy(locale, 'Calendario personalizado', 'Custom calendar')}</option>
                  </Input>
                </div>
              ) : null}
              {alignment === 'ANCHORED' || intervalUnit === 'DAY' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-anchor-date">{copy(locale, 'Fecha de inicio', 'Start date')}</Label>
                  <Input
                    id="budget-anchor-date"
                    type="date"
                    value={anchorDate}
                    onChange={event => setAnchorDate(event.target.value)}
                    disabled={submitting}
                    data-cy="budgetStartDate"
                  />
                  <div className="form-text">
                    {copy(
                      locale,
                      'Puedes empezar en una fecha pasada, actual o futura.',
                      'You can start on a past, current, or future date.',
                    )}
                  </div>
                </div>
              ) : null}
              {alignment === 'NATURAL' && intervalUnit === 'WEEK' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-natural-week">{copy(locale, 'Semana ISO', 'ISO week')}</Label>
                  <Input
                    id="budget-natural-week"
                    type="week"
                    value={naturalWeek}
                    onChange={event => setNaturalWeek(event.target.value)}
                    disabled={submitting}
                  />
                </div>
              ) : null}
              {alignment === 'NATURAL' && intervalUnit === 'MONTH' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-natural-month">{copy(locale, 'Mes', 'Month')}</Label>
                  <Input
                    id="budget-natural-month"
                    type="month"
                    value={naturalMonth}
                    onChange={event => setNaturalMonth(event.target.value)}
                    disabled={submitting}
                  />
                </div>
              ) : null}
              {alignment === 'NATURAL' && intervalUnit === 'QUARTER' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-natural-quarter">{copy(locale, 'Trimestre', 'Quarter')}</Label>
                  <Input
                    id="budget-natural-quarter"
                    type="select"
                    value={naturalQuarter}
                    onChange={event => setNaturalQuarter(event.target.value)}
                    disabled={submitting}
                  >
                    {[0, 1, 2, 3].map(quarter => {
                      const year = Number(naturalYear) || Number(today.slice(0, 4));
                      return <option key={quarter} value={`${year}-Q${quarter + 1}`}>{`${year} · Q${quarter + 1}`}</option>;
                    })}
                  </Input>
                </div>
              ) : null}
              {alignment === 'NATURAL' && intervalUnit === 'QUARTER' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-natural-quarter-year">{copy(locale, 'Año', 'Year')}</Label>
                  <Input
                    id="budget-natural-quarter-year"
                    type="number"
                    min="1900"
                    max="9999"
                    value={naturalYear}
                    onChange={event => {
                      const next = event.target.value;
                      setNaturalYear(next);
                      const currentQuarter = naturalQuarter.slice(-2) || 'Q1';
                      setNaturalQuarter(`${next}-${currentQuarter}`);
                    }}
                    disabled={submitting}
                  />
                </div>
              ) : null}
              {alignment === 'NATURAL' && intervalUnit === 'YEAR' ? (
                <div className="col-12 col-md-3">
                  <Label for="budget-natural-year">{copy(locale, 'Año', 'Year')}</Label>
                  <Input
                    id="budget-natural-year"
                    type="number"
                    min="1900"
                    max="9999"
                    value={naturalYear}
                    onChange={event => setNaturalYear(event.target.value)}
                    disabled={submitting}
                  />
                </div>
              ) : null}
              {alignment === 'CUSTOM' && intervalUnit === 'WEEK' ? (
                <>
                  <div className="col-12 col-md-3">
                    <Label for="budget-custom-week">{copy(locale, 'Primer ciclo completo', 'First complete cycle')}</Label>
                    <Input
                      id="budget-custom-week"
                      type="week"
                      value={customWeek}
                      onChange={event => setCustomWeek(event.target.value)}
                      disabled={submitting}
                    />
                  </div>
                  <div className="col-12 col-md-3">
                    <Label for="budget-custom-weekday">{copy(locale, 'La semana comienza', 'Week starts on')}</Label>
                    <Input
                      id="budget-custom-weekday"
                      type="select"
                      value={customWeekday}
                      onChange={event => setCustomWeekday(event.target.value)}
                      disabled={submitting}
                    >
                      {weekdays.map(([value, spanish, english]) => (
                        <option key={value} value={value}>
                          {copy(locale, spanish, english)}
                        </option>
                      ))}
                    </Input>
                  </div>
                </>
              ) : null}
              {alignment === 'CUSTOM' && ['MONTH', 'QUARTER'].includes(intervalUnit) ? (
                <>
                  <div className="col-12 col-md-3">
                    <Label for="budget-custom-cycle-month">{copy(locale, 'Mes del primer ciclo', 'First cycle month')}</Label>
                    <Input
                      id="budget-custom-cycle-month"
                      type="month"
                      value={intervalUnit === 'MONTH' ? customMonth : customQuarter}
                      onChange={event =>
                        intervalUnit === 'MONTH' ? setCustomMonth(event.target.value) : setCustomQuarter(event.target.value)
                      }
                      disabled={submitting}
                    />
                  </div>
                  <div className="col-12 col-md-3">
                    <Label for="budget-custom-day">{copy(locale, 'Día recurrente', 'Recurring day')}</Label>
                    <Input
                      id="budget-custom-day"
                      type="select"
                      value={customDayOfMonth}
                      onChange={event => setCustomDayOfMonth(event.target.value)}
                      disabled={submitting}
                    >
                      {Array.from({ length: 31 }, (_, index) => index + 1).map(day => (
                        <option key={day} value={day}>
                          {day}
                        </option>
                      ))}
                    </Input>
                  </div>
                </>
              ) : null}
              {alignment === 'CUSTOM' && intervalUnit === 'YEAR' ? (
                <>
                  <div className="col-12 col-md-3">
                    <Label for="budget-custom-year">{copy(locale, 'Año del primer ciclo', 'First cycle year')}</Label>
                    <Input
                      id="budget-custom-year"
                      type="number"
                      min="1900"
                      max="9999"
                      value={customYear}
                      onChange={event => setCustomYear(event.target.value)}
                      disabled={submitting}
                    />
                  </div>
                  <div className="col-6 col-md-2">
                    <Label for="budget-custom-year-month">{copy(locale, 'Mes', 'Month')}</Label>
                    <Input
                      id="budget-custom-year-month"
                      type="select"
                      value={customYearMonth}
                      onChange={event => setCustomYearMonth(event.target.value)}
                      disabled={submitting}
                    >
                      {Array.from({ length: 12 }, (_, index) => index + 1).map(month => (
                        <option key={month} value={month}>
                          {month}
                        </option>
                      ))}
                    </Input>
                  </div>
                  <div className="col-6 col-md-2">
                    <Label for="budget-custom-year-day">{copy(locale, 'Día recurrente', 'Recurring day')}</Label>
                    <Input
                      id="budget-custom-year-day"
                      type="select"
                      value={customDayOfMonth}
                      onChange={event => setCustomDayOfMonth(event.target.value)}
                      disabled={submitting}
                    >
                      {Array.from({ length: 31 }, (_, index) => index + 1).map(day => (
                        <option key={day} value={day}>
                          {day}
                        </option>
                      ))}
                    </Input>
                  </div>
                </>
              ) : null}
              {alignment === 'CUSTOM' && ['MONTH', 'QUARTER'].includes(intervalUnit) && numericCustomDay >= 29 ? (
                <div className="col-12">
                  <p className="small text-muted mb-0" data-cy="budgetMonthEndHint">
                    {copy(
                      locale,
                      `En los meses que no tengan día ${numericCustomDay}, el periodo comenzará el último día disponible. Cuando vuelva a existir el día ${numericCustomDay}, se utilizará nuevamente.`,
                      `In months without day ${numericCustomDay}, the period starts on the last available day. It returns to day ${numericCustomDay} whenever available.`,
                    )}
                  </p>
                </div>
              ) : null}
              {alignment === 'CUSTOM' && intervalUnit === 'YEAR' && Number(customYearMonth) === 2 && numericCustomDay === 29 ? (
                <div className="col-12">
                  <p className="small text-muted mb-0" data-cy="budgetLeapYearHint">
                    {copy(
                      locale,
                      'En los años no bisiestos se utilizará el 28 de febrero. En los años bisiestos se recuperará el 29.',
                      'In non-leap years February 28 is used. February 29 returns in leap years.',
                    )}
                  </p>
                </div>
              ) : null}
              <div className="col-12 col-md-6">
                <Label for="budget-recurrence">{copy(locale, 'Frecuencia', 'Frequency')}</Label>
                <Input
                  id="budget-recurrence"
                  type="select"
                  value={recurrenceMode}
                  onChange={event => setRecurrenceMode(event.target.value as RecurrenceMode)}
                  disabled={submitting}
                >
                  <option value="RECURRING">{copy(locale, 'Recurrente', 'Recurring')}</option>
                  <option value="ONCE">{copy(locale, 'Una sola vez', 'One time')}</option>
                </Input>
              </div>
              {recurrenceMode === 'RECURRING' ? (
                <>
                  <div className="col-12 col-md-6">
                    <Label for="budget-termination">{copy(locale, 'Finalización', 'Ends')}</Label>
                    <Input
                      id="budget-termination"
                      type="select"
                      value={termination}
                      onChange={event => setTermination(event.target.value as Termination)}
                      disabled={submitting}
                    >
                      <option value="NONE">{copy(locale, 'Sin fecha final', 'No end date')}</option>
                      <option value="AFTER_N_PERIODS">{copy(locale, 'Después de períodos completos', 'After complete periods')}</option>
                      <option value="END_DATE">{copy(locale, 'En una fecha de límite completa', 'At a complete boundary date')}</option>
                    </Input>
                  </div>
                  {termination === 'AFTER_N_PERIODS' ? (
                    <div className="col-12 col-md-6">
                      <Label for="budget-termination-count">{copy(locale, 'Número de períodos', 'Number of periods')}</Label>
                      <Input
                        id="budget-termination-count"
                        type="number"
                        min="1"
                        step="1"
                        value={terminationCount}
                        onChange={event => setTerminationCount(event.target.value)}
                        disabled={submitting}
                      />
                    </div>
                  ) : null}
                  {termination === 'END_DATE' ? (
                    <div className="col-12 col-md-6">
                      <Label for="budget-termination-date">{copy(locale, 'Fecha de límite', 'Boundary date')}</Label>
                      <Input
                        id="budget-termination-date"
                        type="date"
                        min={effectiveAnchorDate || today}
                        value={terminationDate}
                        onChange={event => setTerminationDate(event.target.value)}
                        disabled={submitting}
                      />
                    </div>
                  ) : null}
                </>
              ) : null}
            </div>
            <div className="mt-4 border rounded-2 p-3 bg-light" aria-live="polite" data-cy="budgetCalendarPreview">
              <div className="d-flex justify-content-between align-items-center gap-2 mb-2">
                <strong>{copy(locale, 'Vista previa del calendario', 'Calendar preview')}</strong>
                {previewLoading ? <span className="small text-muted">{copy(locale, 'Actualizando…', 'Updating…')}</span> : null}
              </div>
              <p className="small text-muted mb-2">{recurrenceSummary()}</p>
              {previewError ? (
                <div className="small text-danger" role="alert">
                  {previewError}
                </div>
              ) : null}
              {preview?.periods.length ? (
                <ol className="small mb-0 ps-3">
                  {preview.periods.map(period => (
                    <li key={period.index}>{dateRange(period.startInclusive, period.endExclusive, locale)}</li>
                  ))}
                </ol>
              ) : null}
            </div>
          </ProductSection>

          <BudgetScopeSection
            locale={locale}
            submitting={submitting}
            referencesLoading={referencesLoading}
            currency={currency}
            accountScope={accountScope}
            setAccountScope={setAccountScope}
            compatibleAccounts={compatibleAccounts}
            accountIds={accountIds}
            setAccountIds={setAccountIds}
            categoryScope={categoryScope}
            setCategoryScope={setCategoryScope}
            compatibleCategories={compatibleCategories}
            categoryIds={categoryIds}
            setCategoryIds={setCategoryIds}
            tagScope={tagScope}
            setTagScope={setTagScope}
            tagMatchMode={tagMatchMode}
            setTagMatchMode={setTagMatchMode}
            selectedTags={selectedTags}
            tags={tags}
            setTagIds={setTagIds}
            referenceError={referenceError}
          />

          <ProductSection title={copy(locale, '4. Revisar y crear', '4. Review and create')}>
            <div className="row g-3 mb-3">
              <div className="col-12 col-md-6">
                <strong>{copy(locale, 'Objetivo', 'Objective')}:</strong>{' '}
                {type === 'EXPENSE'
                  ? copy(locale, 'Límite de gastos económicos', 'Economic expense limit')
                  : copy(locale, 'Meta de ingresos económicos', 'Economic income goal')}
              </div>
              <div className="col-12 col-md-6">
                <strong>{copy(locale, 'Importe', 'Amount')}:</strong>{' '}
                {amount && currency ? formatBudgetAmount(Number(amount), currency, locale) : '—'}
              </div>
              <div className="col-12 col-md-6">
                <strong>{copy(locale, 'Primer período', 'First period')}:</strong>{' '}
                {preview?.periods[0] ? dateRange(preview.periods[0].startInclusive, preview.periods[0].endExclusive, locale) : '—'}
              </div>
              <div className="col-12 col-md-6">
                <strong>{copy(locale, 'Alcance', 'Scope')}:</strong>{' '}
                {copy(
                  locale,
                  `Cuentas: ${accountScope === 'ALL' ? 'todas' : accountIds.length}; categorías: ${categoryScope === 'ALL' ? 'todas' : categoryIds.length}; etiquetas: ${tagScope === 'NONE' ? 'ninguna' : tagIds.length}.`,
                  `Accounts: ${accountScope === 'ALL' ? 'all' : accountIds.length}; categories: ${categoryScope === 'ALL' ? 'all' : categoryIds.length}; tags: ${tagScope === 'NONE' ? 'none' : tagIds.length}.`,
                )}
              </div>
            </div>
            <Button color="primary" type="submit" disabled={submitting || referencesLoading} data-cy="submitBudget">
              {submitting ? copy(locale, 'Creando presupuesto…', 'Creating budget…') : copy(locale, 'Crear presupuesto', 'Create budget')}
            </Button>
          </ProductSection>
        </div>
      </form>
    </ProductPage>
  );
};

export default BudgetCreate;
