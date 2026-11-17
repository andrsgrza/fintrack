import React, { useEffect, useMemo, useState } from 'react';
import { Button, Input, Label } from 'reactstrap';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { BudgetEvaluation, BudgetOperationalStatus, BudgetOverviewItem, BudgetType, getBudgetOverview } from './budget-api';
import { browserTimeZone, browserToday, copy, dateRange, formatBudgetAmount, userFacingError } from './budget-utils';

const stateLabel = (locale: string, status: BudgetOperationalStatus) =>
  copy(
    locale,
    ({ SCHEDULED: 'Programado', ACTIVE: 'Activo', PAUSED: 'Pausado', COMPLETED: 'Completado' } as Record<string, string>)[status],
    ({ SCHEDULED: 'Scheduled', ACTIVE: 'Active', PAUSED: 'Paused', COMPLETED: 'Completed' } as Record<string, string>)[status],
  );

const evaluationLabel = (locale: string, evaluation: BudgetEvaluation) => {
  if (evaluation.evaluationStatus === 'INCOMPLETE_CONFIGURATION')
    return copy(locale, 'Configuración incompleta', 'Incomplete configuration');
  if (evaluation.evaluationStatus === 'SEMANTIC_UNAVAILABLE') return copy(locale, 'Información no disponible', 'Information unavailable');
  if (evaluation.evaluationStatus === 'SEMANTIC_EXECUTION_FAILED' || evaluation.evaluationStatus === 'SEMANTIC_CONTRACT_ERROR') {
    return copy(locale, 'No se pudo calcular', 'Could not be calculated');
  }
  if (evaluation.evaluationStatus === 'NOT_YET_EVALUATED') return copy(locale, 'Aún no se evalúa', 'Not evaluated yet');
  if (evaluation.evaluationStatus === 'NOT_EVALUATED') return copy(locale, 'Sin evaluación financiera', 'No financial evaluation');
  return null;
};

const goalLabel = (locale: string, evaluation: BudgetEvaluation) => {
  const labels: Record<string, [string, string]> = {
    WITHIN_LIMIT: ['Dentro del límite', 'Within limit'],
    FINISHED_WITHIN_LIMIT: ['Cerró dentro del límite', 'Finished within limit'],
    LIMIT_REACHED: ['Límite consumido', 'Limit reached'],
    EXCEEDED: ['Límite excedido', 'Limit exceeded'],
    GOAL_PENDING: ['Meta pendiente', 'Goal pending'],
    GOAL_NOT_MET: ['Meta no alcanzada', 'Goal not met'],
    GOAL_REACHED: ['Meta alcanzada', 'Goal reached'],
    GOAL_EXCEEDED: ['Meta superada', 'Goal exceeded'],
  };
  const label = labels[evaluation.goalStatus];
  return label ? copy(locale, label[0], label[1]) : null;
};

const BudgetCard = ({ item, locale, referenceDate }: { item: BudgetOverviewItem; locale: string; referenceDate: string }) => {
  const evaluation = item.evaluation;
  const actual = evaluation?.actualAmount;
  const progress = evaluation?.progressPercentage;
  const progressWidth = progress === null || progress === undefined ? 0 : Math.max(0, Math.min(Number(progress), 100));
  const evaluationMessage = evaluation ? evaluationLabel(locale, evaluation) : null;
  const title =
    item.type === 'EXPENSE' ? copy(locale, 'Límite de gasto', 'Expense limit') : copy(locale, 'Meta de ingresos', 'Income goal');
  const noPeriodMessage =
    item.operationalStatus === 'SCHEDULED'
      ? copy(
          locale,
          'Este presupuesto todavía no inicia para la fecha de referencia.',
          'This budget has not started on the reference date.',
        )
      : copy(locale, 'No hay un período programado para esta fecha.', 'There is no scheduled period on this date.');

  return (
    <article className="card border-0 shadow-sm h-100" data-cy={`budgetCard-${item.budgetId}`}>
      <div className="card-body p-3 p-md-4 d-flex flex-column gap-3">
        <div className="d-flex align-items-start justify-content-between gap-2">
          <div className="min-w-0">
            <div className="d-flex flex-wrap gap-2 align-items-center mb-1">
              <span className={`badge text-bg-${item.type === 'EXPENSE' ? 'warning' : 'success'}`}>
                {item.type === 'EXPENSE' ? copy(locale, 'Gastos', 'Expenses') : copy(locale, 'Ingresos', 'Income')}
              </span>
              <span className="badge text-bg-light border text-dark">{stateLabel(locale, item.operationalStatus)}</span>
            </div>
            <h2 className="h5 mb-1 text-break">{item.name}</h2>
            {item.description ? <p className="small text-muted mb-0 text-break">{item.description}</p> : null}
          </div>
          <span className="small fw-semibold text-nowrap">{item.currency}</span>
        </div>

        {item.hasPeriodAtReferenceDate && evaluation ? (
          <>
            <div className="small text-muted">
              {copy(locale, 'Periodo', 'Period')}: {dateRange(evaluation.startInclusive, evaluation.endExclusive, locale)}
            </div>
            <div className="row g-3">
              <div className="col-6">
                <div className="text-muted text-uppercase small fw-semibold">{title}</div>
                <div className="fs-5 fw-semibold">{formatBudgetAmount(evaluation.targetAmount, item.currency, locale)}</div>
              </div>
              <div className="col-6">
                <div className="text-muted text-uppercase small fw-semibold">
                  {item.type === 'EXPENSE' ? copy(locale, 'Gastado', 'Spent') : copy(locale, 'Ingresado', 'Received')}
                </div>
                <div className="fs-5 fw-semibold" data-cy="budgetActual">
                  {evaluation.evaluationApplicable ? formatBudgetAmount(actual, item.currency, locale) : '—'}
                </div>
              </div>
            </div>
            {evaluation.evaluationApplicable && progress !== null ? (
              <>
                <div className="d-flex justify-content-between gap-2 small">
                  <span>{goalLabel(locale, evaluation)}</span>
                  <strong>{Number(progress).toFixed(2)}%</strong>
                </div>
                <div
                  className="progress"
                  role="progressbar"
                  aria-label={copy(locale, 'Progreso del presupuesto', 'Budget progress')}
                  aria-valuenow={Number(progress)}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div
                    className={`progress-bar ${Number(progress) > 100 ? 'bg-danger' : item.type === 'EXPENSE' ? 'bg-warning' : 'bg-success'}`}
                    style={{ width: `${progressWidth}%` }}
                  />
                </div>
                <div className="small text-muted">
                  {evaluation.remainingAmount !== null
                    ? evaluation.remainingAmount >= 0
                      ? `${copy(locale, 'Restante', 'Remaining')}: ${formatBudgetAmount(evaluation.remainingAmount, item.currency, locale)}`
                      : `${copy(locale, 'Excedido', 'Exceeded')}: ${formatBudgetAmount(Math.abs(evaluation.remainingAmount), item.currency, locale)}`
                    : null}
                </div>
              </>
            ) : (
              <div className="small border-start border-3 border-info ps-2" role="status">
                {evaluationMessage ??
                  evaluation.message ??
                  copy(locale, 'Sin información financiera para este periodo.', 'No financial information for this period.')}
              </div>
            )}
          </>
        ) : (
          <div className="small border-start border-3 border-secondary ps-2" role="status">
            {noPeriodMessage}
          </div>
        )}

        <div className="mt-auto pt-1">
          <Button tag={Link} color="secondary" outline size="sm" to={`/budgets/${item.budgetId}?referenceDate=${referenceDate}`}>
            {copy(locale, 'Abrir presupuesto', 'Open budget')}
          </Button>
        </div>
      </div>
    </article>
  );
};

const BudgetOverview = () => {
  const locale = useAppSelector(state => state.locale.currentLocale) || 'en';
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [referenceDate, setReferenceDate] = useState(searchParams.get('referenceDate') ?? browserToday());
  const [type, setType] = useState<BudgetType | ''>('');
  const [status, setStatus] = useState<BudgetOperationalStatus | ''>('');
  const [items, setItems] = useState<BudgetOverviewItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const timeZone = useMemo(browserTimeZone, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    void getBudgetOverview({ referenceDate, timeZone, type, status })
      .then(result => {
        if (active) setItems(result.budgets);
      })
      .catch(problem => {
        if (active)
          setError(userFacingError(problem, copy(locale, 'No se pudieron cargar los presupuestos.', 'Budgets could not be loaded.')));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [locale, referenceDate, status, timeZone, type]);

  return (
    <ProductPage wide className="budget-overview">
      <ProductPageHeader
        title={copy(locale, 'Presupuestos', 'Budgets')}
        subtitle={copy(
          locale,
          'Define límites de gasto y metas de ingresos. Cada tarjeta se interpreta con la misma fecha de referencia.',
          'Set expense limits and income goals. Every card is interpreted with the same reference date.',
        )}
        actions={
          <Button color="primary" onClick={() => navigate('/budgets/new')} data-cy="createBudget">
            {copy(locale, 'Crear presupuesto', 'Create budget')}
          </Button>
        }
        dataCy="budgetHeading"
      />

      <ProductSection title={copy(locale, 'Fecha de referencia', 'Reference date')} className="mb-4">
        <div className="row g-3 align-items-end">
          <div className="col-12 col-md-4">
            <Label for="budget-reference-date">
              {copy(locale, 'Consultar periodos correspondientes al día', 'View periods corresponding to this day')}
            </Label>
            <div className="d-flex gap-2">
              <Input
                id="budget-reference-date"
                type="date"
                value={referenceDate}
                onChange={event => setReferenceDate(event.target.value)}
                data-cy="budgetReferenceDate"
              />
              <Button
                color="secondary"
                outline
                type="button"
                onClick={() => setReferenceDate(browserToday())}
                data-cy="budgetReferenceToday"
              >
                {copy(locale, 'Hoy', 'Today')}
              </Button>
            </div>
          </div>
          <div className="col-12 col-md-4">
            <Label for="budget-type-filter">{copy(locale, 'Objetivo', 'Objective')}</Label>
            <Input id="budget-type-filter" type="select" value={type} onChange={event => setType(event.target.value as BudgetType | '')}>
              <option value="">{copy(locale, 'Todos los presupuestos', 'All budgets')}</option>
              <option value="EXPENSE">{copy(locale, 'Gastos', 'Expenses')}</option>
              <option value="INCOME">{copy(locale, 'Ingresos', 'Income')}</option>
            </Input>
          </div>
          <div className="col-12 col-md-4">
            <Label for="budget-status-filter">{copy(locale, 'Estado operativo', 'Operational state')}</Label>
            <Input
              id="budget-status-filter"
              type="select"
              value={status}
              onChange={event => setStatus(event.target.value as BudgetOperationalStatus | '')}
            >
              <option value="">{copy(locale, 'Todos los estados', 'All states')}</option>
              {(['ACTIVE', 'SCHEDULED', 'PAUSED', 'COMPLETED'] as BudgetOperationalStatus[]).map(value => (
                <option key={value} value={value}>
                  {stateLabel(locale, value)}
                </option>
              ))}
            </Input>
          </div>
        </div>
        <p className="small text-muted mb-0 mt-3">
          {copy(
            locale,
            'Cambiar esta fecha sólo elige qué período de cada presupuesto se muestra; no modifica presupuestos ni movimientos financieros.',
            'Changing this date only chooses which period of each budget is shown; it does not modify budgets or financial transactions.',
          )}{' '}
          {copy(locale, 'Zona horaria actual del navegador', 'Current browser time zone')}: {timeZone}
        </p>
      </ProductSection>

      {error ? (
        <div className="alert alert-danger" role="alert" data-cy="budgetOverviewError">
          {error}
        </div>
      ) : null}
      {loading ? <div className="text-center py-5 text-muted">{copy(locale, 'Cargando presupuestos…', 'Loading budgets…')}</div> : null}
      {!loading && !error && !items.length ? (
        <ProductSection title={copy(locale, 'Aún no tienes presupuestos', 'You do not have budgets yet')}>
          <p className="mb-3">
            {copy(
              locale,
              'Crea un presupuesto para seguir gastos o ingresos sin duplicar movimientos.',
              'Create a budget to follow expenses or income without duplicating transactions.',
            )}
          </p>
          <Button color="primary" onClick={() => navigate('/budgets/new')}>
            {copy(locale, 'Crear mi primer presupuesto', 'Create my first budget')}
          </Button>
        </ProductSection>
      ) : null}
      {!loading && !error && items.length ? (
        <div className="row g-3" data-cy="budgetCards">
          {items.map(item => (
            <div className="col-12 col-lg-6" key={item.budgetId}>
              <BudgetCard item={item} locale={locale} referenceDate={referenceDate} />
            </div>
          ))}
        </div>
      ) : null}
    </ProductPage>
  );
};

export default BudgetOverview;
