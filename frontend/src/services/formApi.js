import apiClient from './apiClient';

export const formApi = {
  // Get all active forms catalog
  getAllForms: async () => {
    const res = await apiClient.get('/forms');
    return res.data;
  },

  // Get schema of a specific form by formCode
  getFormByCode: async (formCode) => {
    const res = await apiClient.get(`/forms/${formCode}`);
    return res.data;
  },

  // Evaluate form rules & workflow step handlers
  evaluateForm: async (formCode, stepCodes, triggerField, formData) => {
    const res = await apiClient.post('/forms/evaluate', {
      formCode,
      stepCodes,
      triggerField,
      formData,
    });
    return res.data; // { updates: {...}, errors: {...}, disabledFields: [...], hiddenFields: [...] }
  },
};
