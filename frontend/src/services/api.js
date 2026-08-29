import axios from 'axios';
import { API_BASE_URL, STORAGE_KEYS } from '../utils/constants';

// Create axios instance
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to handle errors
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // If token expired, try to refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
        if (!refreshToken) {
          // No refresh token to fall back on — this 401 can't be recovered from,
          // so clear stale state instead of silently leaving the app authenticated
          // with a token that no longer works.
          Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key));
          window.location.href = '/login';
          return Promise.reject(error);
        }

        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken,
        });

        const { accessToken } = response.data.data;
        localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh failed, clear only auth keys and redirect
        Object.values(STORAGE_KEYS).forEach(key => localStorage.removeItem(key));
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
