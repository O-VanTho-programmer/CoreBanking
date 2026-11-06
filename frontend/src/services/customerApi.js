import apiClient from './apiClient';

export const customerApi = {
  // Get active customer types from catalog
  getCatalogue: async () => {
    const res = await apiClient.get('/customers/catalogue');
    return res.data;
  },

  // Search customers by keyword and advance filters
  searchCustomers: async (params = {}) => {
    const res = await apiClient.get('/customers/search', { params });
    return res.data;
  },

  // Get customer by CIF number
  getByCif: async (cifNumber) => {
    const res = await apiClient.get(`/customers/${cifNumber}`);
    return res.data;
  },

  // Create new customer profile
  createCustomer: async (payload) => {
    const res = await apiClient.post('/customers', payload);
    return res.data;
  },
};
