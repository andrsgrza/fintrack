import dayjs from 'dayjs';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { IApiAccessToken } from 'app/shared/model/api-access-token.model';

export interface IApiIngestion {
  id?: number;
  requestId?: string;
  idempotencyKey?: string | null;
  sourceSystem?: string | null;
  apiVersion?: string;
  endpoint?: string;
  clientReference?: string | null;
  receivedAt?: dayjs.Dayjs;
  createdAt?: dayjs.Dayjs;
  apiTokenIdSnapshot?: number;
  apiTokenPrefixSnapshot?: string;
  apiTokenNameSnapshot?: string;
  transactionIngestion?: ITransactionIngestion;
  /** Create-only: resolved server-side into snapshot fields. */
  apiAccessToken?: IApiAccessToken;
}

export const defaultValue: Readonly<IApiIngestion> = {};
