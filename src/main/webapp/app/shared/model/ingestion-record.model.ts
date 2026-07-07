import dayjs from 'dayjs';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { IngestionRecordStatus } from 'app/shared/model/enumerations/ingestion-record-status.model';

export interface IIngestionRecord {
  id?: number;
  recordIndex?: number;
  externalRecordId?: string | null;
  status?: keyof typeof IngestionRecordStatus;
  rawData?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt?: dayjs.Dayjs;
  financialTransaction?: IFinancialTransaction | null;
  transactionIngestion?: ITransactionIngestion;
}

export const defaultValue: Readonly<IIngestionRecord> = {};
