import axios from 'axios';

export interface IDescriptionReevaluationRequest {
  recordIds?: number[];
  apply?: boolean;
  protectManualChanges?: boolean;
}

export interface IDescriptionReevaluationResult {
  ingestionRecordId?: number;
  recordIndex?: number;
  action?: string;
  error?: string | null;
  suggestedDescription?: string | null;
}

export interface IDescriptionReevaluationResponse {
  transactionIngestionId?: number;
  rows?: IDescriptionReevaluationResult[];
}

const ingestionApiUrl = 'api/transaction-ingestions';

export const reevaluateIngestionDescriptions = (
  ingestionId: number,
  recordIds?: number[],
  apply?: boolean,
  protectManualChanges?: boolean,
) => {
  const request =
    recordIds?.length || apply !== undefined || protectManualChanges !== undefined
      ? ({
          ...(recordIds?.length ? { recordIds } : {}),
          ...(apply !== undefined ? { apply } : {}),
          ...(protectManualChanges !== undefined ? { protectManualChanges } : {}),
        } satisfies IDescriptionReevaluationRequest)
      : undefined;

  return axios.post<IDescriptionReevaluationResponse>(`${ingestionApiUrl}/${ingestionId}/descriptions/reevaluate`, request);
};
