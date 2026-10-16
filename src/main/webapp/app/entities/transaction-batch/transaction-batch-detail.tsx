import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, FormGroup, Input, Label, Modal, ModalBody, ModalFooter, ModalHeader, Spinner } from 'reactstrap';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { ProductTagSelector } from 'app/shared/ui/product-tag-selector';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { ICategorySelectable } from 'app/shared/model/category-selectable.model';
import { ITagSelectable } from 'app/shared/model/tag-selectable.model';
import {
  BatchCandidate,
  BatchRowMutation,
  BatchReceipt,
  BatchRulePreview,
  BatchRuleSettings,
  TransactionBatchWorkspace,
  addManualRow,
  applyRules,
  appendCsv,
  deleteRow,
  discardBatch,
  getBatch,
  getReceipt,
  patchRow,
  publishBatch,
  previewRules,
  reevaluate,
  reevaluateRow,
  replaceRowTags,
  saveSettings,
  setRowCategory,
} from './transaction-batch-api';
import TransactionBatchRuleCreationModal, {
  ITransactionBatchRuleCreationContext,
  ITransactionBatchRuleCreationRowContext,
  TransactionBatchRuleCreationKind,
} from './transaction-batch-rule-creation-modal';
import { ContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import TransactionBatchEditorModal, { BatchEditorValues, BatchSuggestionDomain } from './transaction-batch-editor-modal';
import { formatBatchAmount, signedAmountForFlow } from './transaction-batch-presentation';

const operationKey = () => (globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID() : `batch-${Date.now()}-${Math.random()}`);

const categoryStyle = (color?: string | null): React.CSSProperties | undefined =>
  color ? { borderInlineStart: `0.3rem solid ${color}`, backgroundColor: `${color}12` } : undefined;

const AppliedBy = ({ attribution, href }: { attribution?: { id?: number | null; name?: string | null } | null; href: string }) => {
  if (!attribution?.name) return null;
  return (
    <div className="small text-muted mt-1" data-testid="transactionBatchAppliedRuleAttribution">
      Aplicada por{' '}
      {attribution.id ? (
        <Link className="transaction-batch-rule-link" to={`${href}/${attribution.id}/view`}>
          {attribution.name}
        </Link>
      ) : (
        attribution.name
      )}
    </div>
  );
};

type BatchEditorSession = { candidate?: BatchCandidate; key: string; createMode: boolean };
type BatchSortKey = 'ROW_POSITION' | 'DATE' | 'AMOUNT' | 'DESCRIPTION' | 'CATEGORY';
type BatchSortDirection = 'ASC' | 'DESC';

const editorErrorFor = (failure: unknown): { message: string; field?: 'category' | 'amount' | 'transactionDate' | 'postingDate' } => {
  const detail = String(
    (failure as { response?: { data?: { detail?: string; message?: string } }; message?: string })?.response?.data?.detail ??
      (failure as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ??
      (failure as { message?: string })?.message ??
      '',
  );
  if (detail.toLocaleLowerCase().includes('category is incompatible with the transaction flow')) {
    return {
      field: 'category',
      message: 'La categoría elegida sólo es válida para el otro tipo de movimiento. Elige otra categoría o cambia el tipo.',
    };
  }
  if (detail.toLocaleLowerCase().includes('signed amount')) {
    return { field: 'amount', message: 'El monto debe ser positivo y tener como máximo dos decimales.' };
  }
  if (detail.toLocaleLowerCase().includes('stale')) {
    return { message: 'Esta fila cambió en otra sesión. Conservamos tu edición local para que la revises antes de reintentar.' };
  }
  if (detail.toLocaleLowerCase().includes('date')) {
    return { field: 'transactionDate', message: 'Revisa las fechas antes de guardar el movimiento.' };
  }
  return { message: 'No se pudo guardar el movimiento. Tu edición sigue abierta para que puedas corregirla o reintentar.' };
};

const editorPayload = (values: BatchEditorValues) => {
  const signedAmount = signedAmountForFlow(values.amount, values.flow);
  return {
    transactionDate: values.transactionDate || undefined,
    postingDate: values.postingDate || undefined,
    description: values.description.trim() || undefined,
    signedAmount: signedAmount ?? undefined,
    externalReference: values.externalReference.trim() || undefined,
    notes: values.notes.trim() || undefined,
    categoryId: values.categoryId,
    tagIds: values.tags.flatMap(tag => (tag.id ? [tag.id] : [])),
    clearFields: [
      !values.transactionDate ? 'TRANSACTION_DATE' : undefined,
      !values.postingDate ? 'POSTING_DATE' : undefined,
      !values.description.trim() ? 'DESCRIPTION' : undefined,
      signedAmount === null ? 'SIGNED_AMOUNT' : undefined,
      !values.externalReference.trim() ? 'EXTERNAL_REFERENCE' : undefined,
      !values.notes.trim() ? 'NOTES' : undefined,
    ].filter(Boolean),
  };
};

const classificationChanged = (candidate: BatchCandidate, values: BatchEditorValues) => ({
  categoryChanged: values.categoryId !== (candidate.categoryId ?? undefined),
  tagsChanged:
    values.tags.length !== candidate.tags.length || values.tags.some(tag => !candidate.tags.some(current => current.id === tag.id)),
});

const DomainSuggestion = ({
  title,
  value,
  ruleName,
  ruleHref,
  applyLabel,
  showApply,
  pending,
  onApply,
}: {
  title: string;
  value?: React.ReactNode;
  ruleName?: string | null;
  ruleHref?: string;
  applyLabel: string;
  showApply: boolean;
  pending: boolean;
  onApply: () => void;
}) => {
  if (!value) return null;
  return (
    <div className="transaction-batch-domain-suggestion mt-2">
      <div className="small text-muted">{title}</div>
      <div className="fw-semibold">{value}</div>
      {ruleName ? (
        <div className="small text-muted">
          Sugerido por{' '}
          {ruleHref ? (
            <Link className="transaction-batch-rule-link" to={ruleHref}>
              {ruleName}
            </Link>
          ) : (
            ruleName
          )}
        </div>
      ) : null}
      {showApply ? (
        <Button size="sm" color="primary" outline className="mt-2" onClick={onApply} disabled={pending}>
          {applyLabel}
        </Button>
      ) : null}
    </div>
  );
};

const RowActions = ({
  row,
  pending,
  onEdit,
  onCreateRule,
  onRemove,
}: {
  row: BatchCandidate;
  pending: boolean;
  onEdit: () => void;
  onCreateRule: (kind: TransactionBatchRuleCreationKind, row: BatchCandidate) => void;
  onRemove: () => void;
}) => (
  <div className="transaction-batch-row-detail d-flex flex-wrap align-items-start gap-2">
    <Button size="sm" color="primary" outline onClick={onEdit} disabled={pending}>
      Editar
    </Button>
    <details className="transaction-batch-row-actions">
      <summary className="btn btn-sm btn-outline-secondary">Más acciones</summary>
      <div className="d-flex flex-column align-items-start gap-2 mt-2">
        <Button
          size="sm"
          color="secondary"
          outline
          onClick={() => onCreateRule('TRANSACTION', row)}
          disabled={pending}
          data-cy={`transactionBatchCreateTransactionRule-${row.id}`}
          data-testid={`transactionBatchCreateTransactionRule-${row.id}`}
        >
          Crear regla de clasificación
        </Button>
        {row.source === 'FILE_IMPORT' && row.sourceDescription?.trim() ? (
          <Button
            size="sm"
            color="secondary"
            outline
            onClick={() => onCreateRule('NORMALIZATION', row)}
            disabled={pending}
            data-cy={`transactionBatchCreateNormalizationRule-${row.id}`}
            data-testid={`transactionBatchCreateNormalizationRule-${row.id}`}
          >
            Crear regla de descripción
          </Button>
        ) : null}
        <Button size="sm" color="danger" outline onClick={onRemove} disabled={pending}>
          Eliminar
        </Button>
      </div>
    </details>
  </div>
);

const TransactionBatchDetail = () => {
  const { id } = useParams<'id'>();
  const navigate = useNavigate();
  const [batch, setBatch] = useState<TransactionBatchWorkspace>();
  const batchRef = useRef<TransactionBatchWorkspace>();
  const rowMutationQueue = useRef<Promise<void>>(Promise.resolve());
  const [categories, setCategories] = useState<ICategorySelectable[]>([]);
  const [tags, setTags] = useState<ITagSelectable[]>([]);
  const [previews, setPreviews] = useState<BatchRulePreview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editor, setEditor] = useState<BatchEditorSession>();
  const [editorSaving, setEditorSaving] = useState(false);
  const [editorError, setEditorError] = useState('');
  const [editorFieldError, setEditorFieldError] = useState<'category' | 'amount' | 'transactionDate' | 'postingDate' | null>(null);
  const [file, setFile] = useState<File>();
  const [appendKey, setAppendKey] = useState<string>();
  const [csvConfirmOpen, setCsvConfirmOpen] = useState(false);
  const [appendUploading, setAppendUploading] = useState(false);
  const [appendFeedback, setAppendFeedback] = useState('');
  const [publishOpen, setPublishOpen] = useState(false);
  const [receipt, setReceipt] = useState<BatchReceipt>();
  const [publishing, setPublishing] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settings, setSettings] = useState<BatchRuleSettings>();
  const [filter, setFilter] = useState<'ALL' | 'NEEDS_ATTENTION' | 'MANUAL' | 'CSV'>('ALL');
  const [pendingRowIds, setPendingRowIds] = useState<number[]>([]);
  const [expandedMobileRowIds, setExpandedMobileRowIds] = useState<number[]>([]);
  const [sortKey, setSortKey] = useState<BatchSortKey>('ROW_POSITION');
  const [sortDirection, setSortDirection] = useState<BatchSortDirection>('ASC');
  const [ruleCreationContext, setRuleCreationContext] = useState<ITransactionBatchRuleCreationContext | null>(null);
  const [ruleCreationFeedback, setRuleCreationFeedback] = useState<{ color: 'success'; message: string } | null>(null);
  const filePickerRef = useRef<HTMLInputElement>(null);

  const replaceWorkspace = useCallback((workspace?: TransactionBatchWorkspace) => {
    batchRef.current = workspace;
    setBatch(workspace);
  }, []);

  const applyRowMutation = useCallback(
    (mutation: BatchRowMutation) => {
      const current = batchRef.current;
      if (!current) return;
      replaceWorkspace({
        ...current,
        version: mutation.batchVersion,
        candidates: current.candidates.some(candidate => candidate.id === mutation.candidate.id)
          ? current.candidates.map(candidate => (candidate.id === mutation.candidate.id ? mutation.candidate : candidate))
          : [...current.candidates, mutation.candidate],
      });
    },
    [replaceWorkspace],
  );

  const queueRowMutation = useCallback(<T,>(rowId: number, action: () => Promise<T>) => {
    setPendingRowIds(current => (current.includes(rowId) ? current : [...current, rowId]));
    const queued = rowMutationQueue.current.then(action, action);
    rowMutationQueue.current = queued.then(
      () => undefined,
      () => undefined,
    );
    return queued.finally(() => setPendingRowIds(current => current.filter(candidateId => candidateId !== rowId)));
  }, []);

  const reload = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const workspace = await getBatch(id);
      replaceWorkspace(workspace);
      const categoryIds = workspace.candidates.flatMap(row => (row.categoryId ? [row.categoryId] : []));
      const tagIds = workspace.candidates.flatMap(row => row.tags.flatMap(tag => (tag.id ? [tag.id] : [])));
      const [nextCategories, nextTags, nextPreviews] = await Promise.all([
        getSelectableCategories(categoryIds),
        getSelectableTags(tagIds),
        previewRules(workspace.id),
      ]);
      setCategories(nextCategories);
      setTags(nextTags);
      setPreviews(nextPreviews);
      setSettings({
        autoApplyDescriptions: workspace.autoApplyDescriptions,
        autoApplyCategories: workspace.autoApplyCategories,
        autoApplyTags: workspace.autoApplyTags,
        protectManualChanges: workspace.protectManualChanges,
      });
      setError('');
      return workspace;
    } catch {
      try {
        if (id) {
          setReceipt(await getReceipt(Number(id)));
          return;
        }
      } catch {
        // The receipt is intentionally bounded. Fall through to the useful recovery error below.
      }
      setError('No se pudo cargar este lote abierto. Puede haber sido publicado, descartado o no estar disponible.');
    } finally {
      setLoading(false);
    }
  }, [id, replaceWorkspace]);
  useEffect(() => {
    reload();
  }, [reload]);

  const categoryOptions = useMemo(() => categories.filter(category => category.active !== false), [categories]);
  const provisionalCandidateId = editor?.createMode ? editor.candidate?.id : undefined;
  const queuedCandidates = batch?.candidates.filter(row => row.id !== provisionalCandidateId) ?? [];
  const invalidCount = queuedCandidates.filter(row => row.validationStatus !== 'VALID').length;
  const visibleRows = useMemo(() => {
    if (!batch) return [];
    const rows = batch.candidates.filter(row => {
      if (row.id === provisionalCandidateId) return false;
      if (filter === 'NEEDS_ATTENTION') return row.validationStatus !== 'VALID' || row.inputIssues.length > 0;
      if (filter === 'MANUAL') return row.source === 'MANUAL';
      if (filter === 'CSV') return row.source === 'FILE_IMPORT';
      return true;
    });
    const direction = sortDirection === 'ASC' ? 1 : -1;
    const text = (value?: string | null) => value?.trim().toLocaleLowerCase('es-MX') ?? '';
    const comparison = (left: BatchCandidate, right: BatchCandidate) => {
      if (sortKey === 'ROW_POSITION') return left.rowPosition - right.rowPosition;
      if (sortKey === 'DATE') return text(left.transactionDate).localeCompare(text(right.transactionDate), 'es-MX');
      if (sortKey === 'AMOUNT') return (left.signedAmount ?? 0) - (right.signedAmount ?? 0);
      if (sortKey === 'DESCRIPTION') return text(left.description).localeCompare(text(right.description), 'es-MX');
      return text(left.categoryName).localeCompare(text(right.categoryName), 'es-MX');
    };
    return [...rows].sort((left, right) => {
      const primary = comparison(left, right);
      return primary === 0 ? left.rowPosition - right.rowPosition || left.id - right.id : primary * direction;
    });
  }, [batch, filter, provisionalCandidateId, sortDirection, sortKey]);
  const previewFor = (row: BatchCandidate) => previews.find(preview => preview.candidateId === row.id);
  const tagName = (tagId: number) => tags.find(tag => tag.id === tagId)?.name ?? `Etiqueta #${tagId}`;
  const categoryName = (categoryId: number) => categories.find(category => category.id === categoryId)?.name ?? `Categoría #${categoryId}`;
  const openNewEditor = () => {
    setEditorError('');
    setEditorFieldError(null);
    setEditor({ key: `new-${Date.now()}`, createMode: true });
  };
  const openEditor = (candidate: BatchCandidate) => {
    setEditorError('');
    setEditorFieldError(null);
    setEditor({ candidate, key: `row-${candidate.id}-${candidate.version}`, createMode: false });
  };
  const closeEditor = async () => {
    if (editorSaving) return;
    if (batch && editor?.createMode && editor.candidate) {
      setEditorSaving(true);
      setEditorError('');
      try {
        await deleteRow(batch.id, editor.candidate.id, editor.candidate.version);
        await reload();
      } catch {
        setEditorError('No se pudo cancelar esta creación. El borrador provisional sigue abierto para que puedas reintentar o guardarlo.');
        return;
      } finally {
        setEditorSaving(false);
      }
    }
    setEditor(undefined);
    setEditorError('');
    setEditorFieldError(null);
  };
  const upload = async () => {
    if (!batch || !file) return;
    const key = appendKey ?? operationKey();
    setAppendKey(key);
    setAppendUploading(true);
    try {
      const result = await appendCsv(batch.id, batch.version, key, file);
      setAppendFeedback(
        `${file.name}: se añadieron ${result.acceptedCandidateCount ?? result.appendedCandidateIds?.length ?? 0} fila${
          (result.acceptedCandidateCount ?? result.appendedCandidateIds?.length ?? 0) === 1 ? '' : 's'
        }${result.evaluationStatus ? ` · evaluación ${result.evaluationStatus.toLocaleLowerCase()}` : ''}.`,
      );
      setFile(undefined);
      setAppendKey(undefined);
      setCsvConfirmOpen(false);
      if (filePickerRef.current) filePickerRef.current.value = '';
      await reload();
    } catch {
      setError('No se pudo añadir el CSV. Conservamos la misma clave para que puedas reintentar esta intención sin duplicar filas.');
    } finally {
      setAppendUploading(false);
    }
  };
  const persistEditorValues = async (values: BatchEditorValues) => {
    const workspace = batchRef.current;
    const session = editor;
    if (!workspace || !session) return undefined;
    setEditorSaving(true);
    setEditorError('');
    setEditorFieldError(null);
    try {
      const mutation = session.candidate
        ? await patchRow(workspace.id, session.candidate.id, {
            ...editorPayload(values),
            expectedCandidateVersion: session.candidate.version,
            ...classificationChanged(session.candidate, values),
          })
        : await addManualRow(workspace.id, workspace.version, editorPayload(values));
      applyRowMutation(mutation);
      setEditor(current => (current?.key === session.key ? { ...current, candidate: mutation.candidate } : current));
      try {
        setPreviews(await previewRules(workspace.id));
      } catch {
        setEditorError(
          'Se guardó el borrador, pero no se pudieron actualizar las sugerencias. Puedes seguir editando o intentar de nuevo.',
        );
      }
      return mutation.candidate;
    } catch (failure) {
      const mapped = editorErrorFor(failure);
      setEditorFieldError(mapped.field ?? null);
      setEditorError(mapped.message);
      return undefined;
    } finally {
      setEditorSaving(false);
    }
  };
  const saveEditor = async (values: BatchEditorValues) => {
    if (!editor) return;
    const hasData = Boolean(
      values.transactionDate ||
        values.postingDate ||
        values.description.trim() ||
        values.amount ||
        values.categoryId ||
        values.tags.length ||
        values.externalReference.trim() ||
        values.notes.trim(),
    );
    if (!editor.candidate && !hasData) {
      setEditor(undefined);
      return;
    }
    const persisted = await persistEditorValues(values);
    if (!persisted) return;
    setEditor(undefined);
    await reload();
  };
  const updateCategory = async (row: BatchCandidate, categoryId: string) => {
    try {
      await queueRowMutation(row.id, async () => {
        const workspace = batchRef.current;
        const currentRow = workspace?.candidates.find(candidate => candidate.id === row.id);
        if (!workspace || !currentRow) return;
        const result = await setRowCategory(workspace.id, currentRow.id, currentRow.version, categoryId ? Number(categoryId) : undefined);
        applyRowMutation(result);
        await reload();
      });
    } catch {
      setError('No se pudo actualizar la categoría; la versión de la fila puede haber cambiado.');
    }
  };
  const updateTags = async (row: BatchCandidate, selected: { id?: number }[]) => {
    try {
      await queueRowMutation(row.id, async () => {
        const workspace = batchRef.current;
        const currentRow = workspace?.candidates.find(candidate => candidate.id === row.id);
        if (!workspace || !currentRow) return;
        const result = await replaceRowTags(
          workspace.id,
          currentRow.id,
          currentRow.version,
          selected.flatMap(tag => (tag.id ? [tag.id] : [])),
        );
        applyRowMutation(result);
        await reload();
      });
    } catch {
      setError('No se pudieron actualizar las etiquetas; la versión de la fila puede haber cambiado.');
    }
  };
  const removeRow = async (row: BatchCandidate) => {
    if (!batch || !window.confirm('¿Eliminar este borrador?')) return;
    try {
      await deleteRow(batch.id, row.id, row.version);
      await reload();
    } catch {
      setError('No se pudo eliminar la fila.');
    }
  };
  const discard = async () => {
    if (!batch || !window.confirm('¿Descartar este lote y todos sus borradores?')) return;
    try {
      await discardBatch(batch.id, batch.version);
      navigate('/transaction-batches');
    } catch {
      setError('No se pudo descartar el lote.');
    }
  };
  const publish = async () => {
    const workspace = batchRef.current;
    if (!workspace || pendingRowIds.length) return;
    setPublishing(true);
    try {
      const result = await publishBatch(workspace.id, workspace.version, operationKey());
      setReceipt(result);
      setPublishOpen(false);
    } catch {
      try {
        setReceipt(await getReceipt(workspace.id));
        setPublishOpen(false);
      } catch {
        setError('No se pudo confirmar la publicación. No se creó una segunda publicación; actualiza el lote antes de reintentar.');
      }
    } finally {
      setPublishing(false);
    }
  };
  const refreshSuggestions = async () => {
    if (!batch) return;
    try {
      await reevaluate(batch.id);
      await reload();
    } catch {
      setError('No se pudieron reevaluar las reglas. Tus valores guardados no cambiaron.');
    }
  };
  const rowRuleCreationContext = (row: BatchCandidate): ITransactionBatchRuleCreationRowContext => ({
    candidateId: row.id,
    rowPosition: row.rowPosition,
    source: row.source,
    // sourceDescription is present only for a temporary FILE_IMPORT candidate. A manual row never receives a
    // fabricated baseline from its editable effective description.
    sourceDescription: row.sourceDescription,
    currentDescription: row.description,
    flow: row.flow,
    accountName: batchRef.current?.accountName,
    categoryId: row.categoryId,
    categoryName: row.categoryName,
    tagIds: row.tags.flatMap(tag => (tag.id ? [tag.id] : [])),
    tagNames: row.tags.map(tag => tag.name ?? ''),
  });
  const openRuleCreation = (kind: TransactionBatchRuleCreationKind, row?: BatchCandidate) => {
    setRuleCreationFeedback(null);
    setRuleCreationContext({ kind, ...(row ? { row: rowRuleCreationContext(row) } : {}) });
  };
  const reportRuleCreated = (rule: { id?: number; name?: string | null }, action: ContextualRuleSaveAction) => {
    const name = rule.name?.trim() || (rule.id ? `Regla #${rule.id}` : 'La regla');
    setRuleCreationFeedback({
      color: 'success',
      message:
        action === 'SAVE'
          ? `${name} se guardó. No se reevaluaron ni modificaron borradores.`
          : `${name} se guardó. La reevaluación se realizará con el alcance elegido.`,
    });
  };
  const reevaluateAfterRuleCreation = async (
    context: ITransactionBatchRuleCreationContext,
    action: Exclude<ContextualRuleSaveAction, 'SAVE'>,
  ) => {
    const workspace = batchRef.current;
    if (!workspace) throw new Error('OPEN_BATCH_NOT_AVAILABLE');
    if (action === 'REEVALUATE_ROW') {
      if (!context.row) throw new Error('ROW_CONTEXT_REQUIRED');
      await reevaluateRow(workspace.id, context.row.candidateId);
    } else {
      // Visual filters only change the queue presentation. The persisted OPEN batch remains the authoritative scope.
      await reevaluate(workspace.id);
    }
    await reload();
    setRuleCreationFeedback({
      color: 'success',
      message:
        action === 'REEVALUATE_ROW' ? 'La regla se guardó y se reevaluó sólo esta fila.' : 'La regla se guardó y se reevaluó todo el lote.',
    });
  };
  const applySuggestion = async (row: BatchCandidate, domain: BatchSuggestionDomain) => {
    if (!batch) return;
    const preview = previewFor(row);
    if (!preview) return;
    const suggestedDescription = preview.suggestedDescription?.trim();
    const applyDescription =
      domain === 'DESCRIPTION' && !batch.autoApplyDescriptions && !!suggestedDescription && suggestedDescription !== row.description;
    const applyCategory = domain === 'CATEGORY' && !batch.autoApplyCategories && !!preview.suggestedCategoryId && !row.categoryId;
    const tagIds = preview.eligibleTagIds.filter(tagId => !row.tags.some(tag => tag.id === tagId));
    const applyTags = domain === 'TAGS' && !batch.autoApplyTags && tagIds.length > 0;
    if (!applyDescription && !applyCategory && !applyTags) return;
    try {
      await applyRules(batch.id, row.id, {
        expectedCandidateVersion: row.version,
        applyDescription,
        expectedSuggestedDescription: applyDescription ? suggestedDescription : undefined,
        applyCategory,
        expectedSuggestedCategoryId: applyCategory ? preview.suggestedCategoryId : undefined,
        applyTags,
        expectedSuggestedTagIds: applyTags ? tagIds : [],
      });
      const workspace = await reload();
      const refreshed = workspace?.candidates.find(candidate => candidate.id === row.id);
      if (refreshed) {
        setEditor(current => (current?.candidate?.id === refreshed.id ? { ...current, candidate: refreshed } : current));
      }
      return refreshed;
    } catch {
      await reload();
      setError('La sugerencia cambió o ya no puede aplicarse. Actualizamos la cola para mostrar el estado actual.');
      return undefined;
    }
  };
  const persistSettings = async () => {
    if (!batch || !settings) return;
    try {
      await saveSettings(batch.id, { expectedBatchVersion: batch.version, ...settings });
      setSettingsOpen(false);
      await reload();
    } catch {
      // Saving settings and evaluating are deliberately separate durable operations. A response can therefore fail
      // after the settings write committed; read the authoritative workspace before claiming that nothing was saved.
      try {
        const persisted = await getBatch(batch.id);
        const settingsPersisted =
          persisted.autoApplyDescriptions === settings.autoApplyDescriptions &&
          persisted.autoApplyCategories === settings.autoApplyCategories &&
          persisted.autoApplyTags === settings.autoApplyTags &&
          persisted.protectManualChanges === settings.protectManualChanges;
        if (settingsPersisted) {
          replaceWorkspace(persisted);
          setSettings({
            autoApplyDescriptions: persisted.autoApplyDescriptions,
            autoApplyCategories: persisted.autoApplyCategories,
            autoApplyTags: persisted.autoApplyTags,
            protectManualChanges: persisted.protectManualChanges,
          });
          setSettingsOpen(false);
          setError('Las preferencias se guardaron, pero no se pudieron reevaluar las reglas. No se aplicaron cambios automáticos.');
          return;
        }
      } catch {
        // The original operation failure remains the actionable message when the authoritative follow-up read fails.
      }
      setError('No se pudieron guardar las preferencias de reglas.');
    }
  };

  if (loading && !batch)
    return (
      <ProductPage>
        <Spinner size="sm" /> Cargando lote…
      </ProductPage>
    );
  if (receipt)
    return (
      <ProductPage>
        <ProductPageHeader title="Transacciones creadas" subtitle="El lote temporal ya se limpió; este receipt técnico caduca." />
        <ProductSection title="Resultado de publicación">
          <p className="mb-2">
            Se crearon <strong>{receipt.publishedTransactionCount}</strong> transacciones definitivas.
          </p>
          <p className="small text-muted">Disponible hasta {new Date(receipt.receiptExpiresAt).toLocaleString()}.</p>
          <Button tag={Link} to="/financial-transaction" color="primary">
            Ver transacciones
          </Button>{' '}
          <Button tag={Link} to="/transaction-batches/new" color="secondary" outline>
            Crear otro lote
          </Button>
        </ProductSection>
      </ProductPage>
    );
  if (!batch)
    return (
      <ProductPage>
        <p className="text-danger">{error}</p>
        <Button tag={Link} to="/transaction-batches">
          Volver a lotes
        </Button>
      </ProductPage>
    );
  return (
    <ProductPage wide>
      <ProductPageHeader
        title="Lote de transacciones"
        subtitle={`${batch.accountName} · ${queuedCandidates.length} borrador${queuedCandidates.length === 1 ? '' : 'es'}`}
        actions={
          <>
            <Button color="primary" onClick={openNewEditor} data-cy="transactionBatchAddManual">
              + Añadir movimiento
            </Button>
            <Button color="secondary" outline onClick={() => filePickerRef.current?.click()} data-cy="transactionBatchImportCsv">
              Importar CSV
            </Button>
            <Button color="secondary" outline onClick={reload}>
              Actualizar
            </Button>
            <Button color="secondary" outline onClick={() => setSettingsOpen(true)} data-cy="transactionBatchSettings">
              Reglas
            </Button>
            <Button
              color="secondary"
              outline
              onClick={() => openRuleCreation('NORMALIZATION')}
              data-cy="transactionBatchCreateNormalizationRule"
              data-testid="transactionBatchCreateNormalizationRule"
            >
              Crear regla de descripción
            </Button>
            <Button
              color="secondary"
              outline
              onClick={() => openRuleCreation('TRANSACTION')}
              data-cy="transactionBatchCreateTransactionRule"
              data-testid="transactionBatchCreateTransactionRule"
            >
              Crear regla de clasificación
            </Button>
            <Button color="secondary" outline onClick={refreshSuggestions} data-cy="transactionBatchReevaluate">
              Reevaluar reglas
            </Button>
            <Button color="danger" outline onClick={discard}>
              Descartar lote
            </Button>
          </>
        }
      />
      {error ? (
        <div role="alert" className="alert alert-danger">
          {error}
        </div>
      ) : null}
      {ruleCreationFeedback ? (
        <div role="status" className={`alert alert-${ruleCreationFeedback.color}`} data-cy="transactionBatchRuleCreationFeedback">
          {ruleCreationFeedback.message}
        </div>
      ) : null}
      <input
        ref={filePickerRef}
        className="visually-hidden"
        type="file"
        accept=".csv,text/csv"
        aria-label="Seleccionar CSV para importar"
        data-cy="transactionBatchCsvFile"
        onChange={event => {
          const selected = event.target.files?.[0];
          if (!selected) return;
          setFile(selected);
          setAppendKey(undefined);
          setCsvConfirmOpen(true);
        }}
      />
      {appendFeedback ? (
        <div role="status" className="alert alert-success" data-cy="transactionBatchAppendFeedback">
          {appendFeedback}
        </div>
      ) : null}
      <ProductSection title="Cola de revisión" dataCy="transactionBatchQueue">
        <div className="d-flex flex-column flex-lg-row align-items-lg-center justify-content-between gap-2 border-bottom pb-3 mb-3">
          <div className="d-flex flex-wrap gap-2" role="group" aria-label="Filtrar borradores">
            {[
              ['ALL', 'Todos'],
              ['NEEDS_ATTENTION', `Por corregir${invalidCount ? ` (${invalidCount})` : ''}`],
              ['MANUAL', 'Manual'],
              ['CSV', 'CSV'],
            ].map(([value, label]) => (
              <Button
                key={value}
                type="button"
                size="sm"
                color={filter === value ? 'primary' : 'secondary'}
                outline={filter !== value}
                onClick={() => setFilter(value as typeof filter)}
              >
                {label}
              </Button>
            ))}
          </div>
          <div className="d-flex align-items-center gap-2">
            <Label className="small text-muted mb-0" for="transaction-batch-sort">
              Ordenar
            </Label>
            <Input
              id="transaction-batch-sort"
              type="select"
              bsSize="sm"
              value={sortKey}
              onChange={event => setSortKey(event.target.value as BatchSortKey)}
              data-cy="transactionBatchSort"
            >
              <option value="ROW_POSITION">Orden de ingreso</option>
              <option value="DATE">Fecha</option>
              <option value="AMOUNT">Importe</option>
              <option value="DESCRIPTION">Descripción</option>
              <option value="CATEGORY">Categoría</option>
            </Input>
            <Button
              type="button"
              size="sm"
              color="secondary"
              outline
              onClick={() => setSortDirection(current => (current === 'ASC' ? 'DESC' : 'ASC'))}
              aria-label={sortDirection === 'ASC' ? 'Orden ascendente' : 'Orden descendente'}
              data-cy="transactionBatchSortDirection"
            >
              {sortDirection === 'ASC' ? '↑' : '↓'}
            </Button>
            <span className="small text-muted">
              {visibleRows.length} de {queuedCandidates.length}
            </span>
          </div>
        </div>
        <div className="transaction-batch-review-grid d-none d-md-grid fw-semibold text-muted small text-uppercase border-bottom pb-2 mb-2">
          <span>Fecha</span>
          <span>Movimiento</span>
          <span>Importe</span>
          <span>Categoría</span>
          <span>Etiquetas</span>
          <span>Detalles</span>
        </div>
        <div className="d-flex flex-column gap-3">
          {visibleRows.map(row => {
            const preview = previewFor(row);
            const suggestedDescription = preview?.suggestedDescription?.trim();
            const descriptionSuggestion = !!suggestedDescription && suggestedDescription !== row.description;
            const categorySuggestion = !!preview?.suggestedCategoryId && !row.categoryId;
            const tagSuggestions = (preview?.eligibleTagIds ?? []).filter(tagId => !row.tags.some(tag => tag.id === tagId));
            const currentCategory = categories.find(category => category.id === row.categoryId);
            const selectableForRow = currentCategory?.active === false ? [currentCategory, ...categoryOptions] : categoryOptions;
            const rowMutationPending = pendingRowIds.includes(row.id);
            const descriptionRuleHref = preview?.descriptionRuleId
              ? `/description-normalization-rule/${preview.descriptionRuleId}/view`
              : undefined;
            const categoryRuleHref = preview?.suggestedCategoryRuleId
              ? `/transaction-rule/${preview.suggestedCategoryRuleId}/view`
              : undefined;
            const firstTagSuggestion = preview?.eligibleTagSuggestions?.find(suggestion => tagSuggestions.includes(suggestion.tagId));
            return (
              <article
                key={row.id}
                className={`transaction-batch-row border rounded-3 p-3${expandedMobileRowIds.includes(row.id) ? ' mobile-expanded' : ''}`}
                data-cy={`transactionBatchRow-${row.id}`}
              >
                <div className="transaction-batch-review-grid d-grid gap-3 align-items-start">
                  <div>
                    <span className="d-md-none small text-muted d-block">Fecha</span>
                    {row.transactionDate || 'Pendiente'}
                  </div>
                  <div>
                    <span className="d-md-none small text-muted d-block">Movimiento</span>
                    <strong>{row.description || 'Sin descripción'}</strong>
                    <AppliedBy attribution={row.descriptionAppliedByRule} href="/description-normalization-rule" />
                    <div className="small text-muted">
                      {row.source === 'MANUAL' ? 'Manual' : 'CSV'} · {row.validationStatus}
                    </div>
                    <div className="transaction-batch-row-detail">
                      <DomainSuggestion
                        title="Descripción sugerida"
                        value={descriptionSuggestion ? suggestedDescription : undefined}
                        ruleName={preview?.descriptionRuleName}
                        ruleHref={descriptionRuleHref}
                        applyLabel="Aplicar descripción"
                        showApply={!batch.autoApplyDescriptions}
                        pending={rowMutationPending}
                        onApply={() => applySuggestion(row, 'DESCRIPTION')}
                      />
                    </div>
                  </div>
                  <div>
                    <span className="d-md-none small text-muted d-block">Importe</span>
                    <strong>{formatBatchAmount(row.signedAmount, batch.accountCurrency ?? row.currencySnapshot ?? 'MXN')}</strong>
                  </div>
                  <div className="transaction-batch-row-detail">
                    <span className="d-md-none small text-muted d-block">Categoría</span>
                    <Input
                      type="select"
                      value={row.categoryId ?? ''}
                      style={categoryStyle(row.categoryColor)}
                      onChange={event => updateCategory(row, event.target.value)}
                      aria-label={`Categoría para ${row.description || 'borrador'}`}
                      disabled={rowMutationPending}
                    >
                      <option value="">Sin categoría</option>
                      {selectableForRow.map(category => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                          {category.active === false ? ' (inactiva)' : ''}
                        </option>
                      ))}
                    </Input>
                    <AppliedBy attribution={row.categoryAppliedByRule} href="/transaction-rule" />
                    <DomainSuggestion
                      title="Categoría sugerida"
                      value={categorySuggestion ? categoryName(preview?.suggestedCategoryId) : undefined}
                      ruleName={preview?.suggestedCategoryRuleName}
                      ruleHref={categoryRuleHref}
                      applyLabel="Aplicar categoría"
                      showApply={!batch.autoApplyCategories}
                      pending={rowMutationPending}
                      onApply={() => applySuggestion(row, 'CATEGORY')}
                    />
                  </div>
                  <div className="transaction-batch-row-detail">
                    <span className="d-md-none small text-muted d-block">Etiquetas</span>
                    <ProductTagSelector
                      selectedTags={row.tags}
                      availableTags={tags}
                      onChange={selected => updateTags(row, selected)}
                      dataCy={`transactionBatchTags-${row.id}`}
                      disabled={rowMutationPending}
                    />
                    {row.tags.map(tag => (
                      <AppliedBy key={tag.id ?? tag.name} attribution={tag.appliedByRule} href="/transaction-rule" />
                    ))}
                    <DomainSuggestion
                      title="Etiquetas sugeridas"
                      value={tagSuggestions.length ? tagSuggestions.map(tagName).join(', ') : undefined}
                      ruleName={firstTagSuggestion?.ruleName}
                      ruleHref={firstTagSuggestion?.ruleId ? `/transaction-rule/${firstTagSuggestion.ruleId}/view` : undefined}
                      applyLabel="Aplicar etiquetas"
                      showApply={!batch.autoApplyTags}
                      pending={rowMutationPending}
                      onApply={() => applySuggestion(row, 'TAGS')}
                    />
                  </div>
                  <div className="d-md-none">
                    <Button
                      size="sm"
                      color="secondary"
                      outline
                      onClick={() =>
                        setExpandedMobileRowIds(current =>
                          current.includes(row.id) ? current.filter(candidateId => candidateId !== row.id) : [...current, row.id],
                        )
                      }
                      aria-expanded={expandedMobileRowIds.includes(row.id)}
                      data-cy={`transactionBatchMobileDetails-${row.id}`}
                    >
                      {expandedMobileRowIds.includes(row.id) ? 'Ocultar detalles' : 'Mostrar detalles'}
                    </Button>
                  </div>
                  <RowActions
                    row={row}
                    pending={rowMutationPending}
                    onEdit={() => openEditor(row)}
                    onCreateRule={openRuleCreation}
                    onRemove={() => removeRow(row)}
                  />
                </div>
                {row.inputIssues.length ? (
                  <div className="small text-danger mt-3">
                    <strong>Requiere corrección:</strong> revisa los datos marcados antes de publicar.
                  </div>
                ) : null}
              </article>
            );
          })}
          {!visibleRows.length ? <p className="text-muted mb-0">No hay borradores con este filtro.</p> : null}
        </div>
      </ProductSection>
      <div className="d-flex justify-content-end mt-3">
        <Button
          color="primary"
          disabled={!queuedCandidates.length || invalidCount > 0 || pendingRowIds.length > 0}
          onClick={() => setPublishOpen(true)}
          data-cy="transactionBatchPublish"
        >
          Publicar
        </Button>
      </div>
      {invalidCount ? <p className="small text-danger text-end mt-2">Corrige las filas pendientes antes de publicar.</p> : null}

      <TransactionBatchEditorModal
        open={!!editor}
        sessionId={editor?.key ?? 'closed'}
        candidate={editor?.candidate}
        preview={editor?.candidate ? previewFor(editor.candidate) : undefined}
        categories={categories}
        tags={tags}
        currency={batch.accountCurrency}
        saving={editorSaving}
        error={editorError}
        fieldError={editorFieldError}
        createMode={editor?.createMode ?? false}
        onClose={closeEditor}
        onSave={saveEditor}
        onMaterialChange={persistEditorValues}
        onApplySuggestion={async (domain, values) => {
          const candidate = await persistEditorValues(values);
          return candidate ? applySuggestion(candidate, domain) : undefined;
        }}
      />
      <Modal
        isOpen={csvConfirmOpen}
        toggle={() => {
          if (appendUploading) return;
          setCsvConfirmOpen(false);
          setFile(undefined);
          setAppendKey(undefined);
          if (filePickerRef.current) filePickerRef.current.value = '';
        }}
      >
        <ModalHeader>Importar CSV</ModalHeader>
        <ModalBody>
          <p className="mb-2">
            <strong>Archivo:</strong> {file?.name ?? 'Sin archivo seleccionado'}
          </p>
          <p className="mb-0">Las filas se añadirán a este lote. Los movimientos existentes no serán reemplazados.</p>
        </ModalBody>
        <ModalFooter>
          <Button
            color="secondary"
            outline
            disabled={appendUploading}
            onClick={() => {
              setCsvConfirmOpen(false);
              setFile(undefined);
              setAppendKey(undefined);
              if (filePickerRef.current) filePickerRef.current.value = '';
            }}
          >
            Cancelar
          </Button>
          <Button color="primary" disabled={!file || appendUploading} onClick={upload} data-cy="transactionBatchAppendCsv">
            {appendUploading ? <Spinner size="sm" /> : 'Importar archivo'}
          </Button>
        </ModalFooter>
      </Modal>
      <Modal isOpen={settingsOpen} toggle={() => setSettingsOpen(false)}>
        <ModalHeader toggle={() => setSettingsOpen(false)}>Preferencias de reglas</ModalHeader>
        <ModalBody>
          <p className="small text-muted">
            Las reglas siempre se previsualizan. Activa una opción sólo si quieres que se apliquen de forma conservadora al reevaluar.
          </p>
          {(
            [
              ['autoApplyDescriptions', 'Aplicar automáticamente descripciones normalizadas'],
              ['autoApplyCategories', 'Aplicar automáticamente categorías sugeridas'],
              ['autoApplyTags', 'Aplicar automáticamente etiquetas sugeridas'],
              ['protectManualChanges', 'Proteger cambios manuales'],
            ] as const
          ).map(([key, label]) => (
            <FormGroup check key={key} className="mb-2">
              <Input
                id={`batch-setting-${key}`}
                type="checkbox"
                checked={settings?.[key] ?? false}
                onChange={event => setSettings(current => ({ ...(current ?? ({} as BatchRuleSettings)), [key]: event.target.checked }))}
              />
              <Label check for={`batch-setting-${key}`}>
                {label}
              </Label>
            </FormGroup>
          ))}
        </ModalBody>
        <ModalFooter>
          <Button color="secondary" outline onClick={() => setSettingsOpen(false)}>
            Cancelar
          </Button>
          <Button color="primary" onClick={persistSettings}>
            Guardar y reevaluar
          </Button>
        </ModalFooter>
      </Modal>
      <Modal isOpen={publishOpen} toggle={() => setPublishOpen(false)}>
        <ModalHeader toggle={() => setPublishOpen(false)}>Crear transacciones definitivas</ModalHeader>
        <ModalBody>
          <p>
            <strong>{batch.accountName}</strong> · {queuedCandidates.length} movimientos.
          </p>
          <p className="mb-0">Se usarán los valores guardados. Las sugerencias pendientes no se aplicarán automáticamente.</p>
        </ModalBody>
        <ModalFooter>
          <Button color="secondary" outline onClick={() => setPublishOpen(false)}>
            Seguir editando
          </Button>
          <Button color="primary" disabled={publishing || pendingRowIds.length > 0} onClick={publish}>
            Crear transacciones
          </Button>
        </ModalFooter>
      </Modal>
      <TransactionBatchRuleCreationModal
        context={ruleCreationContext}
        onClose={() => setRuleCreationContext(null)}
        onCreated={reportRuleCreated}
        onReevaluate={reevaluateAfterRuleCreation}
      />
    </ProductPage>
  );
};

export default TransactionBatchDetail;
