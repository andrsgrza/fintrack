import axios from 'axios';

export interface IDescriptionReevaluationRequest {
  recordIds?: number[];
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

export const reevaluateIngestionDescriptions = (ingestionId: number, recordIds?: number[]) =>
  axios.post<IDescriptionReevaluationResponse>(
    `${ingestionApiUrl}/${ingestionId}/descriptions/reevaluate`,
    recordIds?.length ? ({ recordIds } satisfies IDescriptionReevaluationRequest) : undefined,
  );
