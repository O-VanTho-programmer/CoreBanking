import apiClient from './apiClient';

export const transactionApi = {
  deposit: async (payload) => {
    const res = await apiClient.post('/transactions/deposit', payload);
    return res.data;
  },
  withdraw: async (payload) => {
    const res = await apiClient.post('/transactions/withdraw', payload);
    return res.data;
  },
  transfer: async (payload) => {
    const res = await apiClient.post('/transactions/transfer', payload);
    return res.data;
  },
  reverse: async (payload) => {
    const res = await apiClient.post('/transactions/reverse', payload);
    return res.data;
  },
};
