import dayjs from 'dayjs';
import { IUser } from 'app/shared/model/user.model';
import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ICategory } from 'app/shared/model/category.model';
import { ITransactionIngestion } from 'app/shared/model/transaction-ingestion.model';
import { IIngestionRecord } from 'app/shared/model/ingestion-record.model';
import { IFinancialTransaction } from 'app/shared/model/financial-transaction.model';
import { ITag } from 'app/shared/model/tag.model';
import { TransactionCandidateSource } from 'app/shared/model/enumerations/transaction-candidate-source.model';
import { TransactionCandidateStatus } from 'app/shared/model/enumerations/transaction-candidate-status.model';
import { TransactionCandidateValidationStatus } from 'app/shared/model/enumerations/transaction-candidate-validation-status.model';
import { TransactionCandidateDescriptionReviewStatus } from 'app/shared/model/enumerations/transaction-candidate-description-review-status.model';
import { TransactionCandidateClassificationReviewStatus } from 'app/shared/model/enumerations/transaction-candidate-classification-review-status.model';
import { TransactionFlow } from 'app/shared/model/enumerations/transaction-flow.model';
import { CurrencyCode } from 'app/shared/model/enumerations/currency-code.model';

export interface ITransactionCandidate {
  id?: number;
  source?: keyof typeof TransactionCandidateSource;
  status?: keyof typeof TransactionCandidateStatus;
  validationStatus?: keyof typeof TransactionCandidateValidationStatus;
  descriptionReviewStatus?: keyof typeof TransactionCandidateDescriptionReviewStatus;
  classificationReviewStatus?: keyof typeof TransactionCandidateClassificationReviewStatus;
  transactionDate?: dayjs.Dayjs | string | null;
  postingDate?: dayjs.Dayjs | string | null;
  description?: string | null;
  signedAmount?: number | null;
  amount?: number | null;
  flow?: keyof typeof TransactionFlow | null;
  currencySnapshot?: keyof typeof CurrencyCode | null;
  externalReference?: string | null;
  notes?: string | null;
  failureReason?: string | null;
  createdAt?: dayjs.Dayjs | string;
  updatedAt?: dayjs.Dayjs | string;
  postedAt?: dayjs.Dayjs | string | null;
  cancelledAt?: dayjs.Dayjs | string | null;
  failedAt?: dayjs.Dayjs | string | null;
  user?: IUser;
  account?: IFinancialAccount | null;
  category?: ICategory | null;
  transactionIngestion?: ITransactionIngestion | null;
  ingestionRecord?: IIngestionRecord | null;
  financialTransaction?: IFinancialTransaction | null;
  tags?: ITag[] | null;
}

export const defaultValue: Readonly<ITransactionCandidate> = {};
