import api from './api';
import { STORAGE_KEYS } from '../utils/constants';

const authService = {
  // Register Student
  register: async (userData) => {
    const response = await api.post('/auth/student/register', userData);
    return response.data;
  },

  // Login
  login: async (email, password, role) => {
    const endpoint = role === 'STUDENT' 
      ? '/auth/student/login' 
      : role === 'WARDEN' 
      ? '/auth/warden/login' 
      : '/auth/security/login';

    const response = await api.post(endpoint, { email, password });
    const { accessToken, refreshToken, email: userEmail, role: userRole } = response.data.data;

    // Store tokens and user info
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
    localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
    localStorage.setItem(STORAGE_KEYS.USER_EMAIL, userEmail);
    localStorage.setItem(STORAGE_KEYS.USER_ROLE, userRole);

    return response.data;
  },

  // Logout
  logout: async () => {
    try {
      await api.post('/auth/logout');
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      localStorage.clear();
    }
  },

  // Check if user is authenticated
  isAuthenticated: () => {
    return !!localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
  },

  // Get current user info
  getCurrentUser: () => {
    return {
      email: localStorage.getItem(STORAGE_KEYS.USER_EMAIL),
      role: localStorage.getItem(STORAGE_KEYS.USER_ROLE),
    };
  },

  // Refresh token
  refreshToken: async () => {
    const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
    const response = await api.post('/auth/refresh', { refreshToken });
    
    const { accessToken } = response.data.data;
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
    
    return response.data;
  },
};

export default authService;
