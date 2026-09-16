import axios from 'axios';
import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';

const configuredApiUrl = 'api/description-normalization-rules/configured';

export const createConfiguredDescriptionNormalizationRule = (entity: IDescriptionNormalizationRuleConfigured) =>
  axios.post<IDescriptionNormalizationRuleConfigured>(configuredApiUrl, entity);
