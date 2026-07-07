import dayjs from 'dayjs';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { IngestionType } from 'app/shared/model/enumerations/ingestion-type.model';
import { IngestionStatus } from 'app/shared/model/enumerations/ingestion-status.model';

export interface ITransactionIngestion {
  id?: number;
  ingestionType?: keyof typeof IngestionType;
  status?: keyof typeof IngestionStatus;
  sourceLabel?: string | null;
  startedAt?: dayjs.Dayjs;
  completedAt?: dayjs.Dayjs | null;
  recordsReceived?: number;
  recordsCreated?: number;
  recordsSkipped?: number;
  recordsRejected?: number;
  errorMessage?: string | null;
  createdAt?: dayjs.Dayjs;
  accounts?: IFinancialAccount[];
}

export const defaultValue: Readonly<ITransactionIngestion> = {};
