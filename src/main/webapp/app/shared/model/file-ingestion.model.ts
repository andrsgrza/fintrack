import dayjs from 'dayjs';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { ImportFileType } from 'app/shared/model/enumerations/import-file-type.model';

export interface IFileIngestion {
  id?: number;
  originalFilename?: string;
  fileType?: keyof typeof ImportFileType;
  contentType?: string | null;
  fileSizeBytes?: number | null;
  checksum?: string | null;
  storageKey?: string | null;
  parserName?: string | null;
  parserVersion?: string | null;
  statementStartDate?: dayjs.Dayjs | null;
  statementEndDate?: dayjs.Dayjs | null;
  createdAt?: dayjs.Dayjs;
  transactionIngestion?: ITransactionIngestion;
}

export const defaultValue: Readonly<IFileIngestion> = {};
