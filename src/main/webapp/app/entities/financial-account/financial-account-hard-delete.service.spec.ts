import axios from 'axios';

import {
  getFinancialAccountDeletionPreview,
  getFinancialAccountForDeletion,
  hardDeleteFinancialAccount,
} from './financial-account-hard-delete.service';

jest.mock('axios');

describe('financial-account hard-delete client', () => {
  const mockGet = axios.get as jest.Mock;
  const mockDelete = axios.delete as jest.Mock;

  beforeEach(() => {
    mockGet.mockReset();
    mockDelete.mockReset();
  });

  it('uses the dedicated preview and hard-delete endpoints instead of the generic delete path', () => {
    getFinancialAccountForDeletion(42);
    getFinancialAccountDeletionPreview(42);
    hardDeleteFinancialAccount(42);

    expect(mockGet).toHaveBeenNthCalledWith(1, 'api/financial-accounts/42');
    expect(mockGet).toHaveBeenNthCalledWith(2, 'api/financial-accounts/42/deletion-preview');
    expect(mockDelete).toHaveBeenCalledWith('api/financial-accounts/42/hard-delete');
    expect(mockDelete).not.toHaveBeenCalledWith('api/financial-accounts/42');
  });
});
