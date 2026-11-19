import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Input, Label } from 'reactstrap';
import { Link, useParams } from 'react-router-dom';

import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { useAppSelector } from 'app/config/store';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import {
  BudgetEvaluation,
  BudgetHistory,
  BudgetOperationalPeriod,
  BudgetOverviewItem,
  BudgetRevision,
  BudgetRevisionConfiguration,
  BudgetRevisionSchedule,
  cancelBudgetRevision,
  getBudgetEvaluationAtDate,
  getBudgetCurrentEvaluation,
  getBudgetEffectiveRevision,
  getBudgetHistory,
  getBudgetOperationalPeriods,
  getBudgetOverview,
  replaceBudgetRevision,
  scheduleBudgetRevision,
  updateBudgetMetadata,
} from './budget-api';
import { BudgetScopeSection, ScopeMode, TagScopeMode, categoryCompatible } from './budget-create';
import {
  browserTimeZone,
  budgetTargetValidationError,
  calendarValidationError,
  copy,
  dateRange,
  formatBudgetAmount,
  formatBudgetDate,
  userFacingError,
} from './budget-utils';

type EffectiveChoice = 'CURRENT' | 'NEXT' | 'FUTURE';

const newRequestId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return `budget-revision-${crypto.randomUUID()}`;
  return `budget-revision-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
};

const isConflict = (problem: unknown) =>
  typeof problem === 'object' &&
  problem !== null &&
  'response' in problem &&
  (problem as { response?: { status?: number } }).response?.status === 409;

const scopeDescription = (revision: BudgetRevision, locale: string) => {
  const accounts =
    revision.accountScopeMode === 'ALL'
      ? copy(locale, 'todas las cuentas', 'all accounts')
      : copy(locale, 'cuentas seleccionadas', 'selected accounts');
  const categories =
    revision.categoryScopeMode === 'ALL'
      ? copy(locale, 'todas las categorías', 'all categories')
      : copy(locale, 'categorías seleccionadas y sus subcategorías', 'selected categories and their subcategories');
  const tags =
    revision.tagScopeMode === 'NONE'
      ? copy(locale, 'sin etiquetas', 'no tags')
      : copy(
          locale,
          revision.tagMatchMode === 'ALL' ? 'todas las etiquetas elegidas' : 'cualquier etiqueta elegida',
          revision.tagMatchMode === 'ALL' ? 'all selected tags' : 'any selected tag',
        );
  return `${accounts}; ${categories}; ${tags}.`;
};

const scheduleTitle = (schedule: BudgetRevisionSchedule, revision: BudgetRevision | undefined, currency: string, locale: string) =>
  `${formatBudgetDate(schedule.effectivePeriodStart, locale)} · ${revision ? formatBudgetAmount(revision.targetAmount, currency, locale) : copy(locale, 'configuración histórica', 'historical configuration')}`;

// The editor intentionally coordinates one form with three server-authored effective-date choices.
// eslint-disable-next-line complexity
const BudgetEdit = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const { budgetId: rawBudgetId } = useParams();
  const budgetId = Number(rawBudgetId);
  const timeZone = useMemo(browserTimeZone, []);
  const requestId = useRef(newRequestId());
  const metadataInFlight = useRef(false);
  const revisionInFlight = useRef(false);
  const cancellationRequestIds = useRef(new Map<number, string>());

  const [item, setItem] = useState<BudgetOverviewItem | null>(null);
  const [history, setHistory] = useState<BudgetHistory | null>(null);
  const [currentEvaluation, setCurrentEvaluation] = useState<BudgetEvaluation | null>(null);
  const [currentRevision, setCurrentRevision] = useState<BudgetRevision | null>(null);
  const [periods, setPeriods] = useState<BudgetOperationalPeriod[]>([]);
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [categories, setCategories] = useState<ICategorySelectable[]>([]);
  const [tags, setTags] = useState<ITagSelectable[]>([]);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [accountScope, setAccountScope] = useState<ScopeMode>('ALL');
  const [accountIds, setAccountIds] = useState<number[]>([]);
  const [categoryScope, setCategoryScope] = useState<ScopeMode>('ALL');
  const [categoryIds, setCategoryIds] = useState<number[]>([]);
  const [tagScope, setTagScope] = useState<TagScopeMode>('NONE');
  const [tagIds, setTagIds] = useState<number[]>([]);
  const [tagMatchMode, setTagMatchMode] = useState<'ANY' | 'ALL'>('ANY');
  const [effectiveChoice, setEffectiveChoice] = useState<EffectiveChoice>('NEXT');
  const [futureStart, setFutureStart] = useState('');
  const [reviewing, setReviewing] = useState(false);
  const [pendingCancellation, setPendingCancellation] = useState<BudgetRevisionSchedule | null>(null);
  const [loading, setLoading] = useState(true);
  const [referencesLoading, setReferencesLoading] = useState(true);
  const [savingMetadata, setSavingMetadata] = useState(false);
  const [savingRevision, setSavingRevision] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!Number.isSafeInteger(budgetId) || budgetId <= 0) {
      setError(copy(locale, 'El presupuesto no es válido.', 'The budget is not valid.'));
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      let nextHistory: BudgetHistory | null = null;
      let nextEvaluation: BudgetEvaluation | null = null;
      try {
        nextEvaluation = await getBudgetCurrentEvaluation(budgetId);
      } catch {
        // A future or completed calendar can have no current period. Its first durable scheduled boundary is the
        // server-owned fallback for loading immutable editing context.
      }
      nextHistory = await getBudgetHistory(budgetId);
      const initialSchedule = nextHistory.schedules.find(schedule => schedule.active);
      const overviewReferenceDate = nextEvaluation?.startInclusive ?? initialSchedule?.effectivePeriodStart;
      if (!overviewReferenceDate) throw new Error('No Budget period is available.');
      const overview = await getBudgetOverview({ referenceDate: overviewReferenceDate });
      const nextItem = overview.budgets.find(candidate => candidate.budgetId === budgetId) ?? null;
      if (!nextItem) {
        setItem(null);
        setError(
          copy(locale, 'No existe o no tienes acceso a este presupuesto.', 'It does not exist or you do not have access to this budget.'),
        );
        return;
      }
      setItem(nextItem);
      setName(nextItem.name);
      setDescription(nextItem.description ?? '');
      if (!nextEvaluation) {
        const firstRevision = nextHistory.revisions[0];
        if (nextItem.operationalStatus !== 'SCHEDULED' || !firstRevision) throw new Error('No current Budget period is available.');
        // A not-yet-started budget has no current period.  The API resolves its first scheduled period only to
        // display its immutable configuration; mutation stays unavailable until the server has a current period.
        nextEvaluation = await getBudgetEvaluationAtDate(budgetId, firstRevision.anchorDate);
      }
      const [nextRevision, nextPeriods] = await Promise.all([
        getBudgetEffectiveRevision(budgetId, nextEvaluation.startInclusive),
        getBudgetOperationalPeriods(budgetId, {
          firstIndex: nextEvaluation.periodIndex,
          lastIndex: nextEvaluation.periodIndex + 60,
        }),
      ]);
      setHistory(nextHistory);
      setCurrentEvaluation(nextEvaluation);
      setCurrentRevision(nextRevision);
      setPeriods(nextPeriods);
      setAmount(nextRevision.targetAmount);
      setAccountScope(nextRevision.accountScopeMode);
      setAccountIds(nextRevision.accountIds);
      setCategoryScope(nextRevision.categoryScopeMode);
      setCategoryIds(nextRevision.categoryIds);
      setTagScope(nextRevision.tagScopeMode);
      setTagIds(nextRevision.tagIds);
      setTagMatchMode(nextRevision.tagMatchMode);
      const firstFuture = nextPeriods.find(period => period.index === nextEvaluation.periodIndex + 1 && period.status !== 'COMPLETED');
      setFutureStart(firstFuture?.startInclusive ?? '');
      setReviewing(false);
      setPendingCancellation(null);
    } catch (problem) {
      setError(
        userFacingError(problem, copy(locale, 'No se pudo cargar la edición del presupuesto.', 'The budget editor could not be loaded.')),
      );
    } finally {
      setLoading(false);
    }
  }, [budgetId, locale, timeZone]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let active = true;
    setReferencesLoading(true);
    void Promise.all([getSelectableFinancialAccounts(), getSelectableCategories(), getSelectableTags()])
      .then(([nextAccounts, nextCategories, nextTags]) => {
        if (!active) return;
        setAccounts(nextAccounts);
        setCategories(nextCategories);
        setTags(nextTags);
      })
      .catch(problem => {
        if (active)
          setError(userFacingError(problem, copy(locale, 'No se pudieron cargar las opciones.', 'The choices could not be loaded.')));
      })
      .finally(() => {
        if (active) setReferencesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [locale]);

  const compatibleAccounts = useMemo(() => accounts.filter(account => account.currency === item?.currency), [accounts, item?.currency]);
  const compatibleCategories = useMemo(
    () => categories.filter(category => (item ? categoryCompatible(category, item.type) : false)),
    [categories, item],
  );
  const selectedTags = useMemo(() => tags.filter(tag => tag.id !== undefined && tagIds.includes(tag.id)), [tagIds, tags]);
  const currentPeriod = periods.find(period => period.index === currentEvaluation?.periodIndex) ?? null;
  const nextPeriod =
    periods.find(period => period.index === (currentEvaluation?.periodIndex ?? -1) + 1 && period.status !== 'COMPLETED') ?? null;
  const futurePeriods = periods.filter(
    period => period.index > (currentEvaluation?.periodIndex ?? -1) + 1 && period.status !== 'COMPLETED',
  );
  const effectiveStart =
    effectiveChoice === 'CURRENT'
      ? (currentPeriod?.startInclusive ?? '')
      : effectiveChoice === 'NEXT'
        ? (nextPeriod?.startInclusive ?? '')
        : futureStart;
  const selectedFutureSchedule =
    effectiveChoice === 'CURRENT'
      ? null
      : (history?.schedules.find(schedule => schedule.active && schedule.effectivePeriodStart === effectiveStart) ?? null);
  const effectivePeriod = periods.find(period => period.startInclusive === effectiveStart) ?? null;
  const activeFutureSchedules = (history?.schedules ?? []).filter(
    schedule => schedule.active && schedule.effectivePeriodStart > (currentPeriod?.startInclusive ?? ''),
  );
  const completed = item?.operationalStatus === 'COMPLETED';
  const scheduled = item?.operationalStatus === 'SCHEDULED';

  const validateRevision = () => {
    const targetError = budgetTargetValidationError(amount, locale);
    if (targetError) return targetError;
    if (!effectiveStart) return copy(locale, 'Elige un período válido.', 'Choose a valid period.');
    if (accountScope === 'SELECTED' && !accountIds.length)
      return copy(locale, 'Selecciona al menos una cuenta.', 'Select at least one account.');
    if (categoryScope === 'SELECTED' && !categoryIds.length)
      return copy(locale, 'Selecciona al menos una categoría.', 'Select at least one category.');
    if (tagScope === 'SELECTED' && !tagIds.length) return copy(locale, 'Selecciona al menos una etiqueta.', 'Select at least one tag.');
    return null;
  };

  const revisionPayload = (): BudgetRevisionConfiguration | null => {
    if (!currentRevision) return null;
    return {
      targetAmount: amount,
      intervalUnit: currentRevision.intervalUnit,
      intervalCount: currentRevision.intervalCount,
      alignment: currentRevision.alignment,
      anchorDate: currentRevision.anchorDate,
      customDayOfMonth: currentRevision.customDayOfMonth,
      recurrenceMode: currentRevision.recurrenceMode,
      termination: currentRevision.termination,
      terminationPeriodCount: currentRevision.terminationPeriodCount,
      recurrenceEndDate: currentRevision.recurrenceEndDate,
      accountScopeMode: accountScope,
      accountIds: accountScope === 'SELECTED' ? accountIds : [],
      categoryScopeMode: categoryScope,
      categoryIds: categoryScope === 'SELECTED' ? categoryIds : [],
      tagScopeMode: tagScope,
      tagIds: tagScope === 'SELECTED' ? tagIds : [],
      tagMatchMode,
    };
  };

  const saveMetadata = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!history || !item || savingMetadata || metadataInFlight.current) return;
    if (!name.trim()) {
      setError(copy(locale, 'Escribe un nombre para el presupuesto.', 'Enter a budget name.'));
      return;
    }
    setError(null);
    metadataInFlight.current = true;
    setSavingMetadata(true);
    try {
      await updateBudgetMetadata(budgetId, {
        expectedVersion: history.version,
        name: name.trim(),
        description: description.trim() || null,
      });
      setNotice(copy(locale, 'El nombre y la descripción se actualizaron.', 'The name and description were updated.'));
      await load();
    } catch (problem) {
      setError(
        isConflict(problem)
          ? copy(
              locale,
              'Este presupuesto cambió en otro lugar. Recarga y revisa antes de guardar.',
              'This budget changed elsewhere. Reload and review before saving.',
            )
          : userFacingError(problem, copy(locale, 'No se pudieron guardar los datos.', 'The details could not be saved.')),
      );
    } finally {
      metadataInFlight.current = false;
      setSavingMetadata(false);
    }
  };

  const requestRevisionReview = (event: React.FormEvent) => {
    event.preventDefault();
    const invalid = validateRevision();
    if (invalid) {
      setError(invalid);
      return;
    }
    setError(null);
    setReviewing(true);
  };

  const saveRevision = async () => {
    if (!history || scheduled || revisionInFlight.current || savingRevision || !effectiveStart) return;
    const revision = revisionPayload();
    if (!revision) return;
    setError(null);
    revisionInFlight.current = true;
    setSavingRevision(true);
    try {
      const payload = {
        expectedVersion: history.version,
        clientRequestId: requestId.current,
        timeZone,
        applyToCurrentPeriod: effectiveChoice === 'CURRENT',
        effectivePeriodStart: effectiveChoice === 'CURRENT' ? (currentPeriod?.startInclusive ?? null) : effectiveStart,
        revision,
      };
      if (selectedFutureSchedule) await replaceBudgetRevision(budgetId, selectedFutureSchedule.id, payload);
      else await scheduleBudgetRevision(budgetId, payload);
      setNotice(
        effectiveChoice === 'CURRENT'
          ? copy(
              locale,
              'La nueva configuración se aplica a todo el período actual.',
              'The new configuration applies to the entire current period.',
            )
          : copy(locale, 'La revisión quedó programada para el período elegido.', 'The revision is scheduled for the chosen period.'),
      );
      requestId.current = newRequestId();
      await load();
    } catch (problem) {
      setError(
        isConflict(problem)
          ? copy(
              locale,
              'Este presupuesto cambió en otro lugar. Recarga y revisa antes de confirmar.',
              'This budget changed elsewhere. Reload and review before confirming.',
            )
          : calendarValidationError(problem, locale, copy(locale, 'No se pudo guardar la revisión.', 'The revision could not be saved.')),
      );
    } finally {
      revisionInFlight.current = false;
      setSavingRevision(false);
    }
  };

  const cancelPendingRevision = async () => {
    if (!history || !pendingCancellation || savingRevision) return;
    setSavingRevision(true);
    setError(null);
    try {
      await cancelBudgetRevision(budgetId, pendingCancellation.id, {
        expectedVersion: history.version,
        clientRequestId:
          cancellationRequestIds.current.get(pendingCancellation.id) ??
          (() => {
            const next = newRequestId();
            cancellationRequestIds.current.set(pendingCancellation.id, next);
            return next;
          })(),
        timeZone,
      });
      setNotice(
        copy(
          locale,
          'La revisión pendiente fue cancelada. El historial se conserva.',
          'The pending revision was cancelled. History is preserved.',
        ),
      );
      cancellationRequestIds.current.delete(pendingCancellation.id);
      await load();
    } catch (problem) {
      setError(
        isConflict(problem)
          ? copy(
              locale,
              'Este presupuesto cambió en otro lugar. Recarga y revisa antes de cancelar.',
              'This budget changed elsewhere. Reload and review before cancelling.',
            )
          : userFacingError(problem, copy(locale, 'No se pudo cancelar la revisión.', 'The revision could not be cancelled.')),
      );
    } finally {
      setSavingRevision(false);
    }
  };

  if (loading) {
    return <div className="text-center py-5 text-muted">{copy(locale, 'Cargando presupuesto…', 'Loading budget…')}</div>;
  }

  if (!item || !history || !currentEvaluation || !currentRevision) {
    return (
      <ProductPage wide className="budget-edit">
        <ProductPageHeader title={copy(locale, 'Editar presupuesto', 'Edit budget')} dataCy="budgetEditHeading" />
        <div className="alert alert-danger" role="alert">
          {error ?? copy(locale, 'No se pudo abrir este presupuesto.', 'This budget could not be opened.')}
        </div>
        <Button tag={Link} color="secondary" outline to={`/budgets/${budgetId}`}>
          {copy(locale, 'Volver', 'Back')}
        </Button>
      </ProductPage>
    );
  }

  const revisionFor = (schedule: BudgetRevisionSchedule) => history.revisions.find(revision => revision.id === schedule.revisionId);

  return (
    <ProductPage wide className="budget-edit">
      <ProductPageHeader
        title={copy(locale, 'Editar presupuesto', 'Edit budget')}
        subtitle={copy(
          locale,
          'Los importes y alcances se guardan como revisiones inmutables. El calendario, tipo y moneda se conservan.',
          'Amounts and scope are saved as immutable revisions. Calendar, type, and currency remain unchanged.',
        )}
        actions={
          <Button
            tag={Link}
            color="secondary"
            outline
            to={`/budgets/${budgetId}?referenceDate=${currentEvaluation.startInclusive}`}
            data-cy="backToBudgetDetail"
          >
            {copy(locale, 'Volver al presupuesto', 'Back to budget')}
          </Button>
        }
        dataCy="budgetEditHeading"
      />

      {error ? (
        <div className="alert alert-danger" role="alert" data-cy="budgetEditError">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="alert alert-success" role="status" data-cy="budgetEditNotice">
          {notice}
        </div>
      ) : null}

      <ProductSection title={copy(locale, 'Lo que no cambia', 'What does not change')} className="mb-4">
        <div className="row g-3 small">
          <div className="col-12 col-md-4">
            <strong>{copy(locale, 'Tipo', 'Type')}</strong>
            <div>
              {item.type === 'EXPENSE' ? copy(locale, 'Límite de gasto', 'Expense limit') : copy(locale, 'Meta de ingreso', 'Income goal')}
            </div>
          </div>
          <div className="col-12 col-md-4">
            <strong>{copy(locale, 'Moneda', 'Currency')}</strong>
            <div>{item.currency}</div>
          </div>
          <div className="col-12 col-md-4">
            <strong>{copy(locale, 'Calendario y recurrencia', 'Calendar and recurrence')}</strong>
            <div>
              {currentRevision.intervalCount} {currentRevision.intervalUnit.toLowerCase()} ·{' '}
              {formatBudgetDate(currentRevision.anchorDate, locale)}
            </div>
          </div>
        </div>
        <p className="small text-muted mb-0 mt-3">
          {copy(
            locale,
            'Cambiar el calendario requiere una transición futura compatible que no forma parte de esta edición.',
            'Changing the calendar requires a compatible future transition that is not part of this editor.',
          )}
        </p>
      </ProductSection>

      {completed ? (
        <ProductSection title={copy(locale, 'Presupuesto completado', 'Completed budget')}>
          <p className="mb-0">
            {copy(
              locale,
              'Un presupuesto completado conserva su historial y no puede recibir cambios.',
              'A completed budget preserves its history and cannot receive changes.',
            )}
          </p>
        </ProductSection>
      ) : (
        <div className="d-flex flex-column gap-4">
          <form onSubmit={saveMetadata} noValidate>
            <ProductSection title={copy(locale, 'Nombre y descripción', 'Name and description')}>
              <div className="row g-3 align-items-end">
                <div className="col-12 col-md-5">
                  <Label for="budget-edit-name">{copy(locale, 'Nombre', 'Name')}</Label>
                  <Input
                    id="budget-edit-name"
                    value={name}
                    onChange={event => setName(event.target.value)}
                    disabled={savingMetadata}
                    data-cy="budgetEditName"
                  />
                </div>
                <div className="col-12 col-md-5">
                  <Label for="budget-edit-description">{copy(locale, 'Descripción', 'Description')}</Label>
                  <Input
                    id="budget-edit-description"
                    value={description}
                    onChange={event => setDescription(event.target.value)}
                    disabled={savingMetadata}
                    data-cy="budgetEditDescription"
                  />
                </div>
                <div className="col-12 col-md-2">
                  <Button color="primary" type="submit" disabled={savingMetadata} data-cy="saveBudgetMetadata">
                    {savingMetadata ? copy(locale, 'Guardando…', 'Saving…') : copy(locale, 'Guardar', 'Save')}
                  </Button>
                </div>
              </div>
            </ProductSection>
          </form>

          <ProductSection title={copy(locale, 'Configuración vigente', 'Current configuration')}>
            <div className="row g-3">
              <div className="col-12 col-md-4">
                <div className="small text-muted text-uppercase">{copy(locale, 'Período actual', 'Current period')}</div>
                <strong>{dateRange(currentEvaluation.startInclusive, currentEvaluation.endExclusive, locale)}</strong>
              </div>
              <div className="col-12 col-md-4">
                <div className="small text-muted text-uppercase">{copy(locale, 'Objetivo actual', 'Current target')}</div>
                <strong>{formatBudgetAmount(currentRevision.targetAmount, item.currency, locale)}</strong>
              </div>
              <div className="col-12 col-md-4">
                <div className="small text-muted text-uppercase">{copy(locale, 'Alcance actual', 'Current scope')}</div>
                <span>{scopeDescription(currentRevision, locale)}</span>
              </div>
            </div>
          </ProductSection>

          {scheduled ? (
            <ProductSection title={copy(locale, 'Presupuesto programado', 'Scheduled budget')}>
              <p className="mb-0" data-cy="budgetScheduledRevisionState">
                {copy(
                  locale,
                  'Puedes actualizar el nombre y la descripción. La configuración financiera podrá revisarse cuando comience el primer período; el servidor no permite programar cambios antes de que exista un período actual.',
                  'You can update the name and description. Financial configuration can be revised when the first period begins; the server does not allow changes before a current period exists.',
                )}
              </p>
            </ProductSection>
          ) : (
            <>
              <form onSubmit={requestRevisionReview} noValidate>
                <ProductSection title={copy(locale, 'Nueva revisión', 'New revision')}>
                  <p className="small text-muted">
                    {copy(
                      locale,
                      'Esta acción nunca reescribe períodos anteriores. Un cambio actual cubre el período completo, sin prorrateo.',
                      'This never rewrites earlier periods. A current change covers the whole period, without proration.',
                    )}
                  </p>
                  <div className="row g-3 mb-4">
                    <div className="col-12 col-md-4">
                      <Label for="budget-edit-amount">{copy(locale, 'Nuevo objetivo', 'New target')}</Label>
                      <Input
                        id="budget-edit-amount"
                        type="text"
                        inputMode="decimal"
                        value={amount}
                        onChange={event => {
                          setAmount(event.target.value);
                          setReviewing(false);
                        }}
                        disabled={savingRevision}
                        data-cy="budgetEditAmount"
                      />
                    </div>
                    <div className="col-12 col-md-8">
                      <Label>{copy(locale, '¿Cuándo debe aplicar?', 'When should it apply?')}</Label>
                      <div className="d-flex flex-column flex-md-row gap-3 pt-2">
                        <Label check>
                          <Input
                            type="radio"
                            name="budget-effective-choice"
                            checked={effectiveChoice === 'CURRENT'}
                            onChange={() => {
                              setEffectiveChoice('CURRENT');
                              setReviewing(false);
                            }}
                            disabled={!currentPeriod || savingRevision}
                          />{' '}
                          {copy(locale, 'Período actual', 'Current period')}
                        </Label>
                        <Label check>
                          <Input
                            type="radio"
                            name="budget-effective-choice"
                            checked={effectiveChoice === 'NEXT'}
                            onChange={() => {
                              setEffectiveChoice('NEXT');
                              setReviewing(false);
                            }}
                            disabled={!nextPeriod || savingRevision}
                          />{' '}
                          {copy(locale, 'Período siguiente', 'Next period')}
                        </Label>
                        <Label check>
                          <Input
                            type="radio"
                            name="budget-effective-choice"
                            checked={effectiveChoice === 'FUTURE'}
                            onChange={() => {
                              setEffectiveChoice('FUTURE');
                              setFutureStart(futurePeriods[0]?.startInclusive ?? '');
                              setReviewing(false);
                            }}
                            disabled={!futurePeriods.length || savingRevision}
                          />{' '}
                          {copy(locale, 'Período futuro', 'Future period')}
                        </Label>
                      </div>
                    </div>
                    {effectiveChoice === 'FUTURE' ? (
                      <div className="col-12 col-md-5">
                        <Label for="budget-edit-future-period">{copy(locale, 'Límite de período', 'Period boundary')}</Label>
                        <Input
                          id="budget-edit-future-period"
                          type="select"
                          value={futureStart}
                          onChange={event => {
                            setFutureStart(event.target.value);
                            setReviewing(false);
                          }}
                          data-cy="budgetEditFuturePeriod"
                        >
                          {futurePeriods.map(period => (
                            <option value={period.startInclusive} key={period.index}>
                              {dateRange(period.startInclusive, period.endExclusive, locale)}
                            </option>
                          ))}
                        </Input>
                      </div>
                    ) : null}
                  </div>
                  <BudgetScopeSection
                    title={copy(locale, 'Alcance de la nueva revisión', 'New revision scope')}
                    controlPrefix="budget-edit"
                    locale={locale}
                    submitting={savingRevision}
                    referencesLoading={referencesLoading}
                    currency={item.currency}
                    accountScope={accountScope}
                    setAccountScope={value => {
                      setAccountScope(value);
                      setReviewing(false);
                    }}
                    compatibleAccounts={compatibleAccounts}
                    accountIds={accountIds}
                    setAccountIds={next => {
                      setAccountIds(next);
                      setReviewing(false);
                    }}
                    categoryScope={categoryScope}
                    setCategoryScope={value => {
                      setCategoryScope(value);
                      setReviewing(false);
                    }}
                    compatibleCategories={compatibleCategories}
                    categoryIds={categoryIds}
                    setCategoryIds={next => {
                      setCategoryIds(next);
                      setReviewing(false);
                    }}
                    tagScope={tagScope}
                    setTagScope={value => {
                      setTagScope(value);
                      setReviewing(false);
                    }}
                    tagMatchMode={tagMatchMode}
                    setTagMatchMode={value => {
                      setTagMatchMode(value);
                      setReviewing(false);
                    }}
                    selectedTags={selectedTags}
                    tags={tags}
                    setTagIds={next => {
                      setTagIds(next);
                      setReviewing(false);
                    }}
                    referenceError={error}
                  />
                  {!reviewing ? (
                    <Button color="primary" type="submit" disabled={referencesLoading || savingRevision} data-cy="reviewBudgetRevision">
                      {copy(locale, 'Revisar cambio', 'Review change')}
                    </Button>
                  ) : null}
                  {reviewing ? (
                    <div className="alert alert-info mt-4 mb-0" data-cy="budgetRevisionReview">
                      <strong>{copy(locale, 'Revisa el cambio', 'Review the change')}</strong>
                      <div className="mt-2">
                        {copy(locale, 'Se aplicará desde', 'It will apply from')}:{' '}
                        <strong>{effectiveStart ? formatBudgetDate(effectiveStart, locale) : '—'}</strong>.
                      </div>
                      {effectivePeriod ? (
                        <div className="small mt-1">
                          {copy(locale, 'Período completo', 'Complete period')}:{' '}
                          {dateRange(effectivePeriod.startInclusive, effectivePeriod.endExclusive, locale)}
                        </div>
                      ) : null}
                      {selectedFutureSchedule ? (
                        <p className="mb-2 mt-2">
                          {copy(
                            locale,
                            'Ya hay una revisión pendiente en este límite. Al confirmar, la reemplazarás explícitamente y ambas quedarán en el historial.',
                            'There is already a pending revision at this boundary. Confirming explicitly replaces it and preserves both in history.',
                          )}
                        </p>
                      ) : null}
                      <div className="d-flex gap-2 mt-3">
                        <Button type="button" color="secondary" outline onClick={() => setReviewing(false)}>
                          {copy(locale, 'Cancelar', 'Cancel')}
                        </Button>
                        <Button
                          type="button"
                          color="primary"
                          onClick={() => void saveRevision()}
                          disabled={savingRevision}
                          data-cy="confirmBudgetRevision"
                        >
                          {savingRevision
                            ? copy(locale, 'Guardando…', 'Saving…')
                            : selectedFutureSchedule
                              ? copy(locale, 'Reemplazar revisión pendiente', 'Replace pending revision')
                              : copy(locale, 'Confirmar revisión', 'Confirm revision')}
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </ProductSection>
              </form>

              <ProductSection title={copy(locale, 'Revisiones futuras programadas', 'Scheduled future revisions')}>
                {!activeFutureSchedules.length ? (
                  <p className="text-muted mb-0">{copy(locale, 'No hay revisiones pendientes.', 'There are no pending revisions.')}</p>
                ) : (
                  <div className="d-flex flex-column gap-3">
                    {activeFutureSchedules.map(schedule => {
                      const revision = revisionFor(schedule);
                      return (
                        <div className="border rounded-2 p-3" key={schedule.id} data-cy={`pendingRevision-${schedule.id}`}>
                          <div className="d-flex flex-wrap justify-content-between gap-3">
                            <div>
                              <strong>{scheduleTitle(schedule, revision, item.currency, locale)}</strong>
                              <div className="small text-muted mt-1">{revision ? scopeDescription(revision, locale) : null}</div>
                            </div>
                            <Button
                              type="button"
                              color="danger"
                              outline
                              size="sm"
                              onClick={() => setPendingCancellation(schedule)}
                              data-cy={`cancelPendingRevision-${schedule.id}`}
                            >
                              {copy(locale, 'Cancelar revisión', 'Cancel revision')}
                            </Button>
                          </div>
                          {pendingCancellation?.id === schedule.id ? (
                            <div className="alert alert-warning mt-3 mb-0">
                              <div>
                                {copy(
                                  locale,
                                  '¿Cancelar esta revisión pendiente? No se elimina el historial y el período futuro usará la configuración anterior.',
                                  'Cancel this pending revision? History remains and the future period uses the prior configuration.',
                                )}
                              </div>
                              <div className="d-flex gap-2 mt-2">
                                <Button type="button" color="secondary" outline size="sm" onClick={() => setPendingCancellation(null)}>
                                  {copy(locale, 'Conservar', 'Keep')}
                                </Button>
                                <Button
                                  type="button"
                                  color="danger"
                                  size="sm"
                                  onClick={() => void cancelPendingRevision()}
                                  disabled={savingRevision}
                                  data-cy="confirmCancelPendingRevision"
                                >
                                  {copy(locale, 'Confirmar cancelación', 'Confirm cancellation')}
                                </Button>
                              </div>
                            </div>
                          ) : null}
                        </div>
                      );
                    })}
                  </div>
                )}
              </ProductSection>
            </>
          )}
        </div>
      )}
    </ProductPage>
  );
};

export default BudgetEdit;
