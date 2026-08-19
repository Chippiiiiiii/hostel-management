// API Base URL
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

// User Roles
export const ROLES = {
  STUDENT: 'STUDENT',
  WARDEN: 'WARDEN',
  SECURITY_GUARD: 'SECURITY_GUARD'
};

// Outpass Status
export const OUTPASS_STATUS = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  DECLINED: 'DECLINED',
  EXPIRED: 'EXPIRED'
};

// Local Storage Keys
export const STORAGE_KEYS = {
  ACCESS_TOKEN: 'accessToken',
  REFRESH_TOKEN: 'refreshToken',
  USER_EMAIL: 'userEmail',
  USER_ROLE: 'userRole'
};

// Date Format
export const DATE_FORMAT = 'dd/MM/yyyy HH:mm';
export const DATE_INPUT_FORMAT = "yyyy-MM-dd'T'HH:mm";
