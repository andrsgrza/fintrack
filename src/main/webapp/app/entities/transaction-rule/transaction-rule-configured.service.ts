import axios from 'axios';
import { cleanEntity } from 'app/shared/util/entity-utils';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';

const apiUrl = 'api/transaction-rules';
const configuredApiUrl = `${apiUrl}/configured`;

export const getConfiguredTransactionRule = (id: string | number) => axios.get<ITransactionRuleConfigured>(`${apiUrl}/${id}/configured`);

export const createConfiguredTransactionRule = (entity: ITransactionRuleConfigured) =>
  axios.post<ITransactionRuleConfigured>(configuredApiUrl, cleanEntity(entity));

export const updateConfiguredTransactionRule = (entity: ITransactionRuleConfigured) =>
  axios.put<ITransactionRuleConfigured>(`${apiUrl}/${entity.id}/configured`, cleanEntity(entity));
