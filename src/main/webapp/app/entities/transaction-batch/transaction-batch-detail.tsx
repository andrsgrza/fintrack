import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button, Form, FormGroup, Input, Label, Modal, ModalBody, ModalFooter, ModalHeader, Spinner } from 'reactstrap';
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

const operationKey = () => (globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID() : `batch-${Date.now()}-${Math.random()}`);

const categoryStyle = (color?: string | null): React.CSSProperties | undefined =>
  color ? { borderInlineStart: `0.3rem solid ${color}`, backgroundColor: `${color}12` } : undefined;

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
  const [editing, setEditing] = useState<BatchCandidate>();
  const [file, setFile] = useState<File>();
  const [appendKey, setAppendKey] = useState<string>();
  const [publishOpen, setPublishOpen] = useState(false);
  const [receipt, setReceipt] = useState<BatchReceipt>();
  const [publishing, setPublishing] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settings, setSettings] = useState<BatchRuleSettings>();
  const [filter, setFilter] = useState<'ALL' | 'NEEDS_ATTENTION' | 'MANUAL' | 'CSV'>('ALL');
  const [pendingRowIds, setPendingRowIds] = useState<number[]>([]);
  const [expandedMobileRowIds, setExpandedMobileRowIds] = useState<number[]>([]);
  const [ruleCreationContext, setRuleCreationContext] = useState<ITransactionBatchRuleCreationContext | null>(null);
  const [ruleCreationFeedback, setRuleCreationFeedback] = useState<{ color: 'success'; message: string } | null>(null);

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
        candidates: current.candidates.map(candidate => (candidate.id === mutation.candidate.id ? mutation.candidate : candidate)),
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
  const invalidCount = batch?.candidates.filter(row => row.validationStatus !== 'VALID').length ?? 0;
  const visibleRows = useMemo(() => {
    if (!batch) return [];
    return batch.candidates.filter(row => {
      if (filter === 'NEEDS_ATTENTION') return row.validationStatus !== 'VALID' || row.inputIssues.length > 0;
      if (filter === 'MANUAL') return row.source === 'MANUAL';
      if (filter === 'CSV') return row.source === 'FILE_IMPORT';
      return true;
    });
  }, [batch, filter]);
  const previewFor = (row: BatchCandidate) => previews.find(preview => preview.candidateId === row.id);
  const tagName = (tagId: number) => tags.find(tag => tag.id === tagId)?.name ?? `Etiqueta #${tagId}`;
  const categoryName = (categoryId: number) => categories.find(category => category.id === categoryId)?.name ?? `Categoría #${categoryId}`;
  const addRow = async () => {
    if (!batch) return;
    try {
      await addManualRow(batch.id, batch.version);
      await reload();
    } catch {
      setError('No se pudo añadir el borrador. Actualiza el lote e inténtalo de nuevo.');
    }
  };
  const upload = async () => {
    if (!batch || !file) return;
    const key = appendKey ?? operationKey();
    setAppendKey(key);
    try {
      await appendCsv(batch.id, batch.version, key, file);
      setFile(undefined);
      setAppendKey(undefined);
      await reload();
    } catch {
      setError('No se pudo añadir el CSV. Conservamos la misma clave para que puedas reintentar esta intención sin duplicar filas.');
    }
  };
  const saveEdit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!batch || !editing) return;
    try {
      const clearFields = [
        !editing.transactionDate ? 'TRANSACTION_DATE' : undefined,
        !editing.postingDate ? 'POSTING_DATE' : undefined,
        !editing.description?.trim() ? 'DESCRIPTION' : undefined,
        editing.signedAmount === null ? 'SIGNED_AMOUNT' : undefined,
        !editing.externalReference?.trim() ? 'EXTERNAL_REFERENCE' : undefined,
        !editing.notes?.trim() ? 'NOTES' : undefined,
      ].filter(Boolean);
      await patchRow(batch.id, editing.id, {
        expectedCandidateVersion: editing.version,
        transactionDate: editing.transactionDate || undefined,
        postingDate: editing.postingDate || undefined,
        description: editing.description?.trim() || undefined,
        signedAmount: editing.signedAmount ?? undefined,
        externalReference: editing.externalReference?.trim() || undefined,
        notes: editing.notes?.trim() || undefined,
        clearFields,
      });
      setEditing(undefined);
      await reload();
    } catch {
      setError('No se pudo guardar la fila. No se reemplazó tu edición local.');
    }
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
  const applySuggestion = async (row: BatchCandidate) => {
    if (!batch) return;
    const preview = previewFor(row);
    if (!preview) return;
    const suggestedDescription = preview.suggestedDescription?.trim();
    const applyDescription = !!suggestedDescription && suggestedDescription !== row.description;
    const applyCategory = !!preview.suggestedCategoryId && !row.categoryId;
    const tagIds = preview.eligibleTagIds.filter(tagId => !row.tags.some(tag => tag.id === tagId));
    if (!applyDescription && !applyCategory && !tagIds.length) return;
    try {
      await applyRules(batch.id, row.id, {
        expectedCandidateVersion: row.version,
        applyDescription,
        expectedSuggestedDescription: applyDescription ? suggestedDescription : undefined,
        applyCategory,
        expectedSuggestedCategoryId: applyCategory ? preview.suggestedCategoryId : undefined,
        applyTags: tagIds.length > 0,
        expectedSuggestedTagIds: tagIds,
      });
      await reload();
    } catch {
      await reload();
      setError('La sugerencia cambió o ya no puede aplicarse. Actualizamos la cola para mostrar el estado actual.');
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
        subtitle={`${batch.accountName} · ${batch.candidates.length} borrador${batch.candidates.length === 1 ? '' : 'es'}`}
        actions={
          <>
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
      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <ProductSection title="Añadir movimiento">
            <Button color="primary" onClick={addRow} data-cy="transactionBatchAddManual">
              + Añadir movimiento
            </Button>
            <p className="small text-muted mt-2 mb-0">Se guarda un borrador incompleto para que lo completes cuando estés listo.</p>
          </ProductSection>
        </div>
        <div className="col-md-6">
          <ProductSection title="Añadir desde CSV">
            <Input
              type="file"
              accept=".csv,text/csv"
              onChange={event => setFile(event.target.files?.[0])}
              data-cy="transactionBatchCsvFile"
            />
            <Button className="mt-2" color="secondary" disabled={!file} onClick={upload} data-cy="transactionBatchAppendCsv">
              Añadir CSV
            </Button>
            <p className="small text-muted mt-2 mb-0">Añade filas al final; nunca reemplaza borradores guardados.</p>
          </ProductSection>
        </div>
      </div>
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
          <span className="small text-muted">
            {visibleRows.length} de {batch.candidates.length} borradores
          </span>
        </div>
        <div
          className="d-none d-md-grid fw-semibold text-muted small text-uppercase border-bottom pb-2 mb-2"
          style={{ gridTemplateColumns: '1fr 2fr 1fr 1.6fr 2fr auto', gap: '1rem' }}
        >
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
            const hasSuggestion = descriptionSuggestion || categorySuggestion || tagSuggestions.length > 0;
            const currentCategory = categories.find(category => category.id === row.categoryId);
            const selectableForRow = currentCategory?.active === false ? [currentCategory, ...categoryOptions] : categoryOptions;
            const rowMutationPending = pendingRowIds.includes(row.id);
            return (
              <article
                key={row.id}
                className={`transaction-batch-row border rounded-3 p-3${expandedMobileRowIds.includes(row.id) ? ' mobile-expanded' : ''}`}
                data-cy={`transactionBatchRow-${row.id}`}
              >
                <div className="transaction-batch-row-grid d-grid gap-3 align-items-start">
                  <div>
                    <span className="d-md-none small text-muted d-block">Fecha</span>
                    {row.transactionDate || 'Pendiente'}
                  </div>
                  <div>
                    <span className="d-md-none small text-muted d-block">Movimiento</span>
                    <strong>{row.description || 'Sin descripción'}</strong>
                    <div className="small text-muted">
                      {row.source === 'MANUAL' ? 'Manual' : 'CSV'} · {row.validationStatus}
                    </div>
                  </div>
                  <div>
                    <span className="d-md-none small text-muted d-block">Importe</span>
                    {row.signedAmount ?? 'Pendiente'}
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
                  <div className="transaction-batch-row-detail d-flex gap-1">
                    <div className="d-flex flex-wrap gap-1">
                      <Button
                        size="sm"
                        color="secondary"
                        outline
                        onClick={() => openRuleCreation('TRANSACTION', row)}
                        disabled={rowMutationPending}
                        data-cy={`transactionBatchCreateTransactionRule-${row.id}`}
                        data-testid={`transactionBatchCreateTransactionRule-${row.id}`}
                      >
                        Regla de clasificación
                      </Button>
                      {row.source === 'FILE_IMPORT' && row.sourceDescription?.trim() ? (
                        <Button
                          size="sm"
                          color="secondary"
                          outline
                          onClick={() => openRuleCreation('NORMALIZATION', row)}
                          disabled={rowMutationPending}
                          data-cy={`transactionBatchCreateNormalizationRule-${row.id}`}
                          data-testid={`transactionBatchCreateNormalizationRule-${row.id}`}
                        >
                          Regla de descripción
                        </Button>
                      ) : null}
                      <Button size="sm" color="secondary" outline onClick={() => setEditing({ ...row })} disabled={rowMutationPending}>
                        Editar
                      </Button>
                      <Button size="sm" color="danger" outline onClick={() => removeRow(row)} disabled={rowMutationPending}>
                        Eliminar
                      </Button>
                    </div>
                  </div>
                </div>
                {row.inputIssues.length ? (
                  <div className="small text-danger mt-3">
                    <strong>Requiere corrección:</strong> {row.inputIssues.map(issue => `${issue.field}: ${issue.reasonCode}`).join(' · ')}
                  </div>
                ) : null}
                {hasSuggestion ? (
                  <div className="transaction-batch-suggestion mt-3" data-cy={`transactionBatchSuggestion-${row.id}`}>
                    <div className="small text-muted">
                      <strong>Sugerencia de reglas:</strong>{' '}
                      {[
                        descriptionSuggestion ? `Descripción: ${suggestedDescription}` : undefined,
                        categorySuggestion ? `Categoría: ${categoryName(preview?.suggestedCategoryId)}` : undefined,
                        tagSuggestions.length ? `Etiquetas: ${tagSuggestions.map(tagName).join(', ')}` : undefined,
                      ]
                        .filter(Boolean)
                        .join(' · ')}
                    </div>
                    <Button
                      size="sm"
                      color="primary"
                      outline
                      className="mt-2"
                      onClick={() => applySuggestion(row)}
                      disabled={rowMutationPending}
                    >
                      Aplicar sugerencia
                    </Button>
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
          disabled={!batch.candidates.length || invalidCount > 0 || pendingRowIds.length > 0}
          onClick={() => setPublishOpen(true)}
          data-cy="transactionBatchPublish"
        >
          Publicar
        </Button>
      </div>
      {invalidCount ? <p className="small text-danger text-end mt-2">Corrige las filas pendientes antes de publicar.</p> : null}

      <Modal isOpen={!!editing} toggle={() => setEditing(undefined)}>
        <Form onSubmit={saveEdit}>
          <ModalHeader toggle={() => setEditing(undefined)}>Editar movimiento</ModalHeader>
          <ModalBody>
            <FormGroup>
              <Label for="batch-row-date">Fecha</Label>
              <Input
                id="batch-row-date"
                type="date"
                value={editing?.transactionDate || ''}
                onChange={event => setEditing(current => current && { ...current, transactionDate: event.target.value })}
              />
            </FormGroup>
            <FormGroup>
              <Label for="batch-row-posting-date">Fecha de posteo</Label>
              <Input
                id="batch-row-posting-date"
                type="date"
                value={editing?.postingDate || ''}
                onChange={event => setEditing(current => current && { ...current, postingDate: event.target.value })}
              />
            </FormGroup>
            <FormGroup>
              <Label for="batch-row-description">Descripción</Label>
              <Input
                id="batch-row-description"
                value={editing?.description || ''}
                onChange={event => setEditing(current => current && { ...current, description: event.target.value })}
              />
            </FormGroup>
            <FormGroup>
              <Label for="batch-row-amount">Importe con signo</Label>
              <Input
                id="batch-row-amount"
                type="number"
                step="0.01"
                value={editing?.signedAmount ?? ''}
                onChange={event =>
                  setEditing(
                    current => current && { ...current, signedAmount: event.target.value === '' ? null : Number(event.target.value) },
                  )
                }
              />
            </FormGroup>
            <FormGroup>
              <Label for="batch-row-reference">Referencia externa</Label>
              <Input
                id="batch-row-reference"
                value={editing?.externalReference || ''}
                onChange={event => setEditing(current => current && { ...current, externalReference: event.target.value })}
              />
            </FormGroup>
            <FormGroup className="mb-0">
              <Label for="batch-row-notes">Notas</Label>
              <Input
                id="batch-row-notes"
                type="textarea"
                value={editing?.notes || ''}
                onChange={event => setEditing(current => current && { ...current, notes: event.target.value })}
              />
            </FormGroup>
          </ModalBody>
          <ModalFooter>
            <Button type="button" color="secondary" outline onClick={() => setEditing(undefined)}>
              Cancelar
            </Button>
            <Button color="primary">Guardar</Button>
          </ModalFooter>
        </Form>
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
            <strong>{batch.accountName}</strong> · {batch.candidates.length} movimientos.
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
