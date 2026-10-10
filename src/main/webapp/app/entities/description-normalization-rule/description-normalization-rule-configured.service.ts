import axios from 'axios';
import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';

const apiUrl = 'api/description-normalization-rules';
const configuredApiUrl = `${apiUrl}/configured`;

export const createConfiguredDescriptionNormalizationRule = (entity: IDescriptionNormalizationRuleConfigured) =>
  axios.post<IDescriptionNormalizationRuleConfigured>(configuredApiUrl, entity);

export const getConfiguredDescriptionNormalizationRule = (id: string | number) =>
  axios.get<IDescriptionNormalizationRuleConfigured>(`${apiUrl}/${id}/configured`);

export const updateConfiguredDescriptionNormalizationRule = (id: string | number, entity: IDescriptionNormalizationRuleConfigured) =>
  axios.put<IDescriptionNormalizationRuleConfigured>(`${apiUrl}/${id}/configured`, entity);
