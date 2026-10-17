import React, { useEffect, useState } from 'react';
import { Button, Spinner } from 'reactstrap';
import { Link } from 'react-router-dom';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { TransactionBatchWorkspace, listBatches } from './transaction-batch-api';

const TransactionBatch = () => {
  const [batches, setBatches] = useState<TransactionBatchWorkspace[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const load = () => {
    setLoading(true);
    listBatches()
      .then(setBatches)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="transaction-batch-heading"
        dataCy="TransactionBatchHeading"
        title="Lotes de transacciones"
        subtitle="Prepara movimientos guardados antes de crear las transacciones definitivas."
        actions={
          <Button tag={Link} to="/transaction-batches/new" color="primary" data-cy="transactionBatchCreate">
            + Crear lote
          </Button>
        }
      />
      <ProductSection title="Lotes abiertos" dataCy="transactionBatchList">
        {loading ? <Spinner size="sm" aria-label="Cargando lotes" /> : null}
        {error ? <p className="text-danger mb-0">No se pudieron cargar los lotes. Inténtalo de nuevo.</p> : null}
        {!loading && !error && !batches.length ? <p className="text-muted mb-0">No tienes lotes abiertos.</p> : null}
        <div className="d-flex flex-column gap-2">
          {batches.map(batch => (
            <Link key={batch.id} to={`/transaction-batches/${batch.id}`} className="border rounded-3 p-3 text-decoration-none text-body">
              <div className="d-flex justify-content-between gap-3">
                <div>
                  <strong>{batch.accountName}</strong>
                  <div className="small text-muted">
                    {batch.candidates.length} borrador{batch.candidates.length === 1 ? '' : 'es'} · actualizado{' '}
                    {new Date(batch.updatedAt).toLocaleString()}
                  </div>
                </div>
                <span className="badge text-bg-secondary align-self-start">Abierto</span>
              </div>
            </Link>
          ))}
        </div>
      </ProductSection>
    </ProductPage>
  );
};

export default TransactionBatch;
