import apiClient from './apiClient';

export const accountApi = {
  getAccountDetail: async (accountNumber) => {
    const res = await apiClient.get(`/accounts/${accountNumber}`);
    return res.data;
  },
  getAccountsByCustomer: async (customerId) => {
    const res = await apiClient.get(`/accounts/customer/${customerId}`);
    return res.data;
  },
  openAccount: async (payload) => {
    const res = await apiClient.post('/accounts/open', payload);
    return res.data;
  },
};
