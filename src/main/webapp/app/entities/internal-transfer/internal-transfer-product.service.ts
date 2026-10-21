import axios from 'axios';

export type InternalTransferLeg = {
  transactionId: number;
  accountId: number;
  accountName: string;
  accountType?: string | null;
  currency?: string | null;
  accountActive?: boolean | null;
  transactionDate?: string | null;
  postingDate?: string | null;
  description?: string | null;
  externalReference?: string | null;
  notes?: string | null;
  amount?: number | null;
  flow?: string | null;
  origin?: string | null;
  categoryName?: string | null;
  tagNames?: string[] | null;
};

export type InternalTransferRead = {
  transferId: number;
  version: number;
  createdAt?: string | null;
  notes?: string | null;
  transactionDate?: string | null;
  amount?: number | null;
  currency?: string | null;
  source: InternalTransferLeg;
  destination: InternalTransferLeg;
};

export type InternalTransferPage = {
  content: InternalTransferRead[];
  totalElements: number;
  totalPages: number;
};

export type InternalTransferCreateCommand = {
  sourceAccountId: number;
  destinationAccountId: number;
  amount: number;
  transactionDate: string;
  sourcePostingDate?: string;
  destinationPostingDate?: string;
  sourceDescription: string;
  destinationDescription: string;
  sourceExternalReference?: string;
  destinationExternalReference?: string;
  sourceNotes?: string;
  destinationNotes?: string;
  transferNotes?: string;
};

export type InternalTransferCounterpartCommand = {
  targetAccountId: number;
  postingDate?: string;
  description: string;
  externalReference?: string;
  notes?: string;
};

export type InternalTransferCounterpart = {
  transactionId: number;
  accountId: number;
  accountName: string;
  transactionDate?: string | null;
  postingDate?: string | null;
  description?: string | null;
  amount?: number | null;
  flow?: string | null;
  currency?: string | null;
  origin?: string | null;
};

export type InternalTransferCounterpartPage = {
  content: InternalTransferCounterpart[];
  totalElements: number;
  totalPages: number;
};

export type InternalTransferCounterpartSearch = {
  search?: string;
  accountId?: number;
  page?: number;
  size?: number;
};

export type InternalTransferUpdateCommand = {
  expectedVersion: number;
  amount: number;
  transactionDate: string;
  sourcePostingDate?: string;
  destinationPostingDate?: string;
  sourceDescription: string;
  destinationDescription: string;
  notes?: string;
};

export type InternalTransferListParams = {
  accountId?: number;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
  sort?: string;
};

const apiUrl = 'api/internal-transfers';

export const getInternalTransfers = async (params: InternalTransferListParams = {}) => {
  const response = await axios.get<InternalTransferPage>(`${apiUrl}/product`, { params });
  return response.data;
};

export const getInternalTransfer = async (id: string | number) => {
  const response = await axios.get<InternalTransferRead>(`${apiUrl}/product/${id}`);
  return response.data;
};

export const getInternalTransferForTransaction = async (transactionId: string | number) => {
  const response = await axios.get<InternalTransferRead>(`${apiUrl}/transactions/${transactionId}/product`);
  return response.data;
};

export const createInternalTransfer = async (command: InternalTransferCreateCommand) => {
  const response = await axios.post<InternalTransferRead>(`${apiUrl}/commands/create`, command);
  return response.data;
};

export const createCounterpartForTransaction = async (transactionId: number, command: InternalTransferCounterpartCommand) => {
  const response = await axios.post<InternalTransferRead>(`${apiUrl}/transactions/${transactionId}/counterpart`, command);
  return response.data;
};

export const linkExistingTransactions = async (outgoingTransactionId: number, incomingTransactionId: number) => {
  const response = await axios.post<InternalTransferRead>(`${apiUrl}/commands/link-existing`, {
    outgoingTransactionId,
    incomingTransactionId,
  });
  return response.data;
};

export const findEligibleCounterparts = async (transactionId: number, options: InternalTransferCounterpartSearch | string = {}) => {
  const search = typeof options === 'string' ? options : (options.search ?? '');
  const accountId = typeof options === 'string' ? undefined : options.accountId;
  const page = typeof options === 'string' ? 0 : (options.page ?? 0);
  const size = typeof options === 'string' ? 12 : (options.size ?? 12);
  const response = await axios.get<InternalTransferCounterpartPage>(`${apiUrl}/counterparts/${transactionId}`, {
    params: { search, accountId, page, size, sort: 'transactionDate,DESC' },
  });
  return response.data;
};

export const updateInternalTransfer = async (id: string | number, command: InternalTransferUpdateCommand) => {
  const response = await axios.put<InternalTransferRead>(`${apiUrl}/${id}/aggregate`, command);
  return response.data;
};

export const unlinkInternalTransfer = async (id: number, expectedVersion: number) => {
  const response = await axios.post<InternalTransferRead>(`${apiUrl}/${id}/unlink`, { expectedVersion });
  return response.data;
};

/** Typed command errors are safe to map to product copy; unexpected errors stay generic. */
export const internalTransferErrorKey = (error: unknown) => {
  if (!axios.isAxiosError(error)) return 'unexpectedError';
  const detail = String(error.response?.data?.detail ?? error.response?.data?.message ?? '');
  if (detail.includes('TRANSFER_VERSION_CONFLICT')) return 'versionConflict';
  if (detail.includes('TRANSFER_ACCOUNT_MISMATCH') || detail.includes('TRANSFER_SAME_ACCOUNT')) return 'accountMismatch';
  if (detail.includes('TRANSFER_CURRENCY_MISMATCH')) return 'currencyMismatch';
  if (detail.includes('TRANSFER_AMOUNT')) return 'invalidAmount';
  if (detail.includes('TRANSFER_NOT_FOUND')) return 'notFound';
  return 'unexpectedError';
};
