import apiClient from './apiClient';

export const lendingApi = {
  createContract: async (payload) => {
    const res = await apiClient.post('/lending/contracts', payload);
    return res.data;
  },
  disburse: async (payload) => {
    const res = await apiClient.post('/lending/disburse', payload);
    return res.data;
  },
  getLoanDetail: async (loanId) => {
    const res = await apiClient.get(`/lending/${loanId}`);
    return res.data;
  },
  getSchedules: async (loanId) => {
    const res = await apiClient.get(`/lending/${loanId}/schedules`);
    return res.data;
  },
};
