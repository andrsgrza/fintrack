import React, { useRef, useState } from 'react';
import { Button, Dropdown, DropdownItem, DropdownMenu, DropdownToggle, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';

import {
  BudgetHistory,
  BudgetLifecycleCommandPayload,
  BudgetOperationalStatus,
  BudgetOverviewItem,
  cancelBudgetPause,
  deleteBudget,
  requestBudgetFinish,
  requestBudgetPause,
  requestBudgetResume,
} from './budget-api';
import { copy, formatBudgetDate, userFacingError } from './budget-utils';

type LifecycleAction = 'PAUSE' | 'CANCEL_PAUSE' | 'RESUME' | 'FINISH' | 'DELETE';
type LifecycleEvent = BudgetHistory['lifecycleEvents'][number];

const newRequestId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return `budget-lifecycle-${crypto.randomUUID()}`;
  return `budget-lifecycle-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
};

const isConflict = (problem: unknown) =>
  typeof problem === 'object' &&
  problem !== null &&
  'response' in problem &&
  (problem as { response?: { status?: number } }).response?.status === 409;

const isAlreadyDeleted = (problem: unknown) => {
  if (typeof problem !== 'object' || problem === null || !('response' in problem)) return false;
  const response = (problem as { response?: { status?: number; data?: { message?: string } } }).response;
  // The established owner-scoped REST convention returns "Budget not found." as a 400.
  // A second delete from this detail page is therefore safely treated as complete as well.
  return response?.status === 404 || (response?.status === 400 && response.data?.message === 'Budget not found.');
};

const actionLabel = (locale: string, action: LifecycleAction) => {
  const labels: Record<LifecycleAction, [string, string]> = {
    PAUSE: ['Pausar al siguiente período', 'Pause at next period'],
    CANCEL_PAUSE: ['Cancelar pausa pendiente', 'Cancel pending pause'],
    RESUME: ['Reanudar en siguiente período', 'Resume at next period'],
    FINISH: ['Finalizar presupuesto', 'Finish budget'],
    DELETE: ['Eliminar presupuesto', 'Delete budget'],
  };
  return copy(locale, labels[action][0], labels[action][1]);
};

const eventLabel = (locale: string, event: LifecycleEvent) => {
  const labels: Record<string, [string, string]> = {
    PAUSE_REQUESTED: ['Pausa pendiente', 'Pause pending'],
    RESUME_REQUESTED: ['Reanudación pendiente', 'Resume pending'],
    FINISH_REQUESTED: ['Finalización pendiente', 'Finalization pending'],
  };
  const label = labels[event.type];
  return label ? copy(locale, label[0], label[1]) : event.type;
};

export type LifecycleProjection = {
  pendingPause: LifecycleEvent | null;
  pendingResume: LifecycleEvent | null;
  pendingFinish: LifecycleEvent | null;
};

export const lifecycleProjection = (history: BudgetHistory | null, currentPeriodStart: string | null): LifecycleProjection => {
  if (!currentPeriodStart) {
    return { pendingPause: null, pendingResume: null, pendingFinish: null };
  }
  const events = history?.lifecycleEvents ?? [];
  const cancelledPauseIds = new Set(
    events.filter(event => event.type === 'PAUSE_CANCELLED' && event.targetEventId !== null).map(event => event.targetEventId),
  );
  const pending = events.filter(
    event =>
      event.effectivePeriodStart > currentPeriodStart &&
      event.type !== 'PAUSE_CANCELLED' &&
      !(event.type === 'PAUSE_REQUESTED' && cancelledPauseIds.has(event.id)),
  );
  return {
    pendingPause: pending.find(event => event.type === 'PAUSE_REQUESTED') ?? null,
    pendingResume: pending.find(event => event.type === 'RESUME_REQUESTED') ?? null,
    pendingFinish: pending.find(event => event.type === 'FINISH_REQUESTED') ?? null,
  };
};

export const BudgetLifecycleStatus = ({
  locale,
  history,
  currentPeriodStart,
}: {
  locale: string;
  history: BudgetHistory | null;
  currentPeriodStart: string | null;
}) => {
  const pending = lifecycleProjection(history, currentPeriodStart);
  const entries = [pending.pendingPause, pending.pendingResume, pending.pendingFinish].filter(
    (event): event is LifecycleEvent => event !== null,
  );
  if (!entries.length) return null;
  return (
    <div className="small border-start border-3 border-info ps-2 mb-4" role="status" data-cy="budgetPendingLifecycle">
      <strong>{copy(locale, 'Acciones programadas', 'Scheduled actions')}</strong>
      <ul className="mb-0 mt-1 ps-3">
        {entries.map(event => (
          <li key={event.id} data-cy={`budgetPendingLifecycle-${event.id}`}>
            {eventLabel(locale, event)}: {formatBudgetDate(event.effectivePeriodStart, locale)}
          </li>
        ))}
      </ul>
    </div>
  );
};

export const BudgetLifecycleActions = ({
  locale,
  budget,
  history,
  currentPeriodStart,
  nextPeriodStart,
  nextPeriodAvailable,
  timeZone,
  onChanged,
  onSuccess,
  onDeleted,
}: {
  locale: string;
  budget: BudgetOverviewItem;
  history: BudgetHistory | null;
  currentPeriodStart: string | null;
  nextPeriodStart: string | null;
  nextPeriodAvailable: boolean;
  timeZone: string;
  onChanged: () => void;
  onSuccess: (message: string) => void;
  onDeleted: () => void;
}) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [action, setAction] = useState<LifecycleAction | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const requestIds = useRef(new Map<LifecycleAction, string>());
  const pending = lifecycleProjection(history, currentPeriodStart);
  const hasPending = !!pending.pendingPause || !!pending.pendingResume || !!pending.pendingFinish;
  const canSchedule = !!history && !!currentPeriodStart && !!nextPeriodStart && nextPeriodAvailable;
  const actions: LifecycleAction[] = [];

  const selectAction = (event: React.MouseEvent<HTMLElement>, nextAction: LifecycleAction) => {
    // Reactstrap's dropdown also handles click propagation. Close its menu explicitly so a
    // refresh triggered by the prior lifecycle command cannot swallow the confirmation action.
    event.preventDefault();
    event.stopPropagation();
    if (submitting) return;
    setMenuOpen(false);
    setAction(nextAction);
  };

  if (budget.operationalStatus === 'ACTIVE') {
    if (canSchedule && !hasPending) actions.push('PAUSE', 'FINISH');
    if (pending.pendingPause) actions.push('CANCEL_PAUSE');
  }
  if (budget.operationalStatus === 'PAUSED' && canSchedule && !pending.pendingResume && !pending.pendingFinish) {
    actions.push('RESUME', 'FINISH');
  }
  actions.push('DELETE');

  const detail = (nextAction: LifecycleAction) => {
    if (nextAction === 'PAUSE')
      return copy(
        locale,
        `El período actual seguirá activo. La pausa comenzará el ${formatBudgetDate(nextPeriodStart ?? '', locale)}.`,
        `The current period remains active. The pause starts on ${formatBudgetDate(nextPeriodStart ?? '', locale)}.`,
      );
    if (nextAction === 'CANCEL_PAUSE')
      return copy(
        locale,
        'La pausa pendiente se cancelará. El calendario y las revisiones se conservan.',
        'The pending pause will be cancelled. The calendar and revisions remain unchanged.',
      );
    if (nextAction === 'RESUME')
      return copy(
        locale,
        `El período pausado actual no cambia. La reanudación comenzará el ${formatBudgetDate(nextPeriodStart ?? '', locale)}.`,
        `The current paused period does not change. Resumption starts on ${formatBudgetDate(nextPeriodStart ?? '', locale)}.`,
      );
    if (nextAction === 'FINISH')
      return copy(
        locale,
        `La finalización es permanente y será efectiva el ${formatBudgetDate(nextPeriodStart ?? '', locale)}. El historial seguirá disponible y no podrás reanudar el presupuesto.`,
        `Finalization is permanent and takes effect on ${formatBudgetDate(nextPeriodStart ?? '', locale)}. History remains available and the budget cannot be resumed.`,
      );
    return copy(
      locale,
      `Eliminarás “${budget.name}” y todo su historial de presupuestos. Esta acción no se puede recuperar y nunca elimina movimientos, cuentas, categorías ni etiquetas.`,
      `You will delete “${budget.name}” and all of its budget history. This cannot be recovered and never deletes transactions, accounts, categories, or tags.`,
    );
  };

  const submit = async () => {
    if (!action || !history || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      if (action === 'DELETE') {
        try {
          await deleteBudget(budget.budgetId, history.version);
        } catch (problem) {
          if (!isAlreadyDeleted(problem)) throw problem;
        }
        onDeleted();
        return;
      }
      const payload: BudgetLifecycleCommandPayload = {
        expectedVersion: history.version,
        clientRequestId: requestIds.current.get(action) ?? newRequestId(),
        timeZone,
      };
      requestIds.current.set(action, payload.clientRequestId);
      if (action === 'PAUSE') await requestBudgetPause(budget.budgetId, payload);
      if (action === 'CANCEL_PAUSE' && pending.pendingPause) await cancelBudgetPause(budget.budgetId, pending.pendingPause.id, payload);
      if (action === 'RESUME') await requestBudgetResume(budget.budgetId, payload);
      if (action === 'FINISH') await requestBudgetFinish(budget.budgetId, payload);
      requestIds.current.delete(action);
      const successNotice =
        action === 'CANCEL_PAUSE'
          ? copy(locale, 'La pausa pendiente fue cancelada.', 'The pending pause was cancelled.')
          : copy(locale, 'La acción fue programada con la fecha indicada.', 'The action was scheduled for the displayed date.');
      setNotice(successNotice);
      onSuccess(successNotice);
      setAction(null);
      onChanged();
    } catch (problem) {
      setError(
        isConflict(problem)
          ? copy(
              locale,
              'Este presupuesto cambió en otro lugar. Recarga y revisa antes de confirmar.',
              'This budget changed elsewhere. Reload and review before confirming.',
            )
          : userFacingError(problem, copy(locale, 'No se pudo guardar la acción.', 'The action could not be saved.')),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Dropdown isOpen={menuOpen} toggle={() => setMenuOpen(open => !open)}>
        <DropdownToggle color="secondary" outline caret data-cy="budgetLifecycleActions">
          {copy(locale, 'Acciones', 'Actions')}
        </DropdownToggle>
        <DropdownMenu end>
          {actions
            .filter(candidate => candidate !== 'DELETE')
            .map(candidate => (
              <DropdownItem key={candidate} onClick={event => selectAction(event, candidate)} disabled={submitting}>
                {actionLabel(locale, candidate)}
              </DropdownItem>
            ))}
          <DropdownItem divider />
          <DropdownItem className="text-danger" onClick={event => selectAction(event, 'DELETE')} disabled={submitting}>
            {actionLabel(locale, 'DELETE')}
          </DropdownItem>
        </DropdownMenu>
      </Dropdown>
      {error ? (
        <div className="alert alert-danger mb-0" role="alert" data-cy="budgetLifecycleError">
          {error}
        </div>
      ) : null}
      {notice ? (
        <div className="alert alert-success mb-0" role="status" data-cy="budgetLifecycleNotice">
          {notice}
        </div>
      ) : null}
      <Modal isOpen={action !== null} toggle={() => !submitting && setAction(null)} centered>
        <ModalHeader toggle={() => !submitting && setAction(null)}>{action ? actionLabel(locale, action) : ''}</ModalHeader>
        <ModalBody>{action ? detail(action) : null}</ModalBody>
        <ModalFooter>
          <Button color="secondary" outline onClick={() => setAction(null)} disabled={submitting}>
            {copy(locale, 'Cancelar', 'Cancel')}
          </Button>
          <Button
            color={action === 'DELETE' || action === 'FINISH' ? 'danger' : 'primary'}
            onClick={() => void submit()}
            disabled={submitting}
            data-cy="confirmBudgetLifecycle"
          >
            {submitting ? copy(locale, 'Guardando…', 'Saving…') : action ? actionLabel(locale, action) : ''}
          </Button>
        </ModalFooter>
      </Modal>
    </>
  );
};
