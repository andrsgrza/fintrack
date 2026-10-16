import React, { useEffect, useState } from 'react';
import { Button, Form, FormGroup, Input, Label } from 'reactstrap';
import { Link, useNavigate } from 'react-router-dom';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { IFinancialAccountSelectable } from 'app/shared/model/financial-account-selectable.model';
import { createBatch } from './transaction-batch-api';

const TransactionBatchNew = () => {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<IFinancialAccountSelectable[]>([]);
  const [accountId, setAccountId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    getSelectableFinancialAccounts()
      .then(setAccounts)
      .catch(() => setError('No se pudieron cargar las cuentas activas.'));
  }, []);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!accountId) return setError('Selecciona una cuenta antes de crear el lote.');
    setSaving(true);
    setError('');
    try {
      const batch = await createBatch(Number(accountId));
      navigate(`/transaction-batches/${batch.id}`);
    } catch {
      setError('No se pudo crear el lote.');
    } finally {
      setSaving(false);
    }
  };
  return (
    <ProductPage>
      <ProductPageHeader
        title="Crear lote"
        subtitle="La cuenta queda fija mientras existan borradores."
        actions={
          <Button tag={Link} to="/transaction-batches" color="secondary" outline>
            Volver a lotes
          </Button>
        }
      />
      <ProductSection title="Cuenta">
        <Form onSubmit={submit}>
          <FormGroup>
            <Label for="transaction-batch-account">Cuenta</Label>
            <Input
              id="transaction-batch-account"
              data-cy="transactionBatchAccount"
              type="select"
              value={accountId}
              onChange={event => setAccountId(event.target.value)}
            >
              <option value="">Selecciona una cuenta</option>
              {accounts.map(account => (
                <option key={account.id} value={account.id}>
                  {account.name} · {account.currency}
                </option>
              ))}
            </Input>
          </FormGroup>
          {error ? (
            <p className="text-danger" role="alert">
              {error}
            </p>
          ) : null}
          <Button color="primary" disabled={saving} data-cy="transactionBatchCreateSubmit">
            Crear lote
          </Button>
        </Form>
      </ProductSection>
    </ProductPage>
  );
};

export default TransactionBatchNew;
