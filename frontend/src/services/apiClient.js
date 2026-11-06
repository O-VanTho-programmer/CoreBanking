import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
});

// Normalize request URL if it already includes /api/v1 to prevent duplicate path segments
apiClient.interceptors.request.use(
  (config) => {
    if (config.url && config.url.startsWith('/api/v1/')) {
      config.url = config.url.substring('/api/v1'.length);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const errorMsg =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'Server connection error';
    return Promise.reject(new Error(errorMsg));
  }
);

export default apiClient;
