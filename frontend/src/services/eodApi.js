import apiClient from './apiClient';

export const eodApi = {
  getStatus: async () => {
    const res = await apiClient.get('/eod/status');
    return res.data;
  },
  runEod: async (targetWorkingDate) => {
    const res = await apiClient.post('/eod/run', { targetWorkingDate });
    return res.data;
  },
  getTrialBalance: async (workingDate) => {
    const res = await apiClient.get('/eod/trial-balance', {
      params: { workingDate },
    });
    return res.data;
  },
  toggleCutoff: async (closed) => {
    const res = await apiClient.post('/eod/cutoff', null, {
      params: { closed },
    });
    return res.data;
  },
};
